#!/usr/bin/env python3
"""Exercise the production nginx template in isolated, unpublished containers."""

from contextlib import contextmanager
from http.client import HTTPResponse
from io import BytesIO
from pathlib import Path
import json
import subprocess
import time
import unittest
import uuid


ROOT = Path(__file__).resolve().parents[1]


def docker(*args):
    return subprocess.run(
        ["docker", *args], check=True, capture_output=True, text=True, timeout=60
    ).stdout.strip()


class ResponseSocket:
    def __init__(self, raw):
        self.raw = raw

    def makefile(self, *args):
        return BytesIO(self.raw)


class ProxyHeadersTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        # Exercise exactly the production nginx runtime and its OS fixes.
        # This target does not require building the frontend's Node stage.
        cls.image = docker("build", "--quiet", "--target", "runtime-base", str(ROOT / "frontend"))

    @contextmanager
    def container(self, trusted):
        name = "mangashelf-proxy-test-" + uuid.uuid4().hex
        docker(
            "run", "--detach", "--name", name,
            "--add-host", "backend:127.0.0.1",
            "--env", "MS_TRUSTED_PROXY=" + trusted,
            "--env", "NGINX_ENVSUBST_FILTER=^MS_",
            "--volume", f"{ROOT / 'frontend/default.conf.template'}:/etc/nginx/templates/default.conf.template:ro",
            "--volume", f"{ROOT / 'scripts/tests/proxy-backend.conf'}:/etc/nginx/conf.d/backend.conf:ro",
            self.image,
        )
        try:
            # No host ports and no production volumes or credentials are used.
            for _ in range(40):
                try:
                    docker("exec", name, "wget", "-qO-", "http://127.0.0.1:8080/")
                    break
                except subprocess.CalledProcessError:
                    time.sleep(0.25)
            else:
                self.fail("nginx did not become ready: " + docker("logs", name))
            docker("exec", name, "nginx", "-t")
            yield name
        finally:
            docker("rm", "--force", name)

    def request(self, name, path, xff="203.0.113.7", method="GET"):
        request = (
            f"{method} {path} HTTP/1.1\r\nHost: 127.0.0.1\r\nConnection: close\r\n"
            f"X-Forwarded-For: {xff}\r\nX-Forwarded-Proto: https\r\n"
            "X-Forwarded-Host: forged.example\r\n"
            "Forwarded: for=203.0.113.99;proto=https\r\nContent-Length: 0\r\n\r\n"
        )
        # Keep stdin open until the server closes the HTTP connection. BusyBox
        # nc can otherwise exit on stdin EOF before reading the response.
        with subprocess.Popen(
            ["docker", "exec", "-i", name, "nc", "-w", "3", "127.0.0.1", "80"],
            stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=subprocess.PIPE,
        ) as process:
            process.stdin.write(request.encode())
            process.stdin.flush()
            try:
                process.wait(timeout=10)
            except subprocess.TimeoutExpired:
                process.kill()
                raise
            raw = process.stdout.read()
            self.assertEqual(process.returncode, 0, process.stderr.read().decode())
        response = HTTPResponse(ResponseSocket(raw))
        response.begin()
        return response.status, dict(response.getheaders()), response.read().decode()

    def exercise(self, trusted, expected_address, expected_proto, expected_host, xff):
        with self.container(trusted) as name:
            for path in ("/api/probe", "/api/auth/login", "/api/auth/register", "/api/auth/2fa/login"):
                status, _, result = self.request(name, path, xff)
                self.assertEqual(status, 200)
                self.assertEqual(result, "|".join([
                    expected_address, expected_address, expected_proto, expected_host, ""
                ]))

    def test_untrusted_client_cannot_supply_proxy_identity(self):
        self.exercise("192.0.2.10", "127.0.0.1", "http", "127.0.0.1",
                      "203.0.113.7, 10.0.0.8")

    def test_trusted_proxy_preserves_real_client_and_https(self):
        self.exercise("127.0.0.1", "203.0.113.7", "https", "forged.example",
                      "203.0.113.7")

    def test_private_hops_are_not_implicitly_trusted(self):
        self.exercise("127.0.0.1", "10.0.0.8", "https", "forged.example",
                      "203.0.113.7, 10.0.0.8")

    def test_limits_use_real_ip_and_leave_session_reads_and_other_clients_available(self):
        with self.container("127.0.0.1") as name:
            results = [self.request(name, "/api/auth/login", method="POST") for _ in range(20)]
            rejected = [result for result in results if result[0] == 429]
            self.assertTrue(rejected, "login burst was never limited")
            self.assertEqual(json.loads(rejected[0][2]), {"error": "too_many_attempts"})
            self.assertIn("application/json", rejected[0][1]["Content-Type"])
            # A query string or switching to MFA must not create a fresh bucket.
            self.assertEqual(self.request(name, "/api/auth/2fa/login?attempt=next", method="POST")[0], 429)
            self.assertEqual(self.request(name, "/api/auth/login", "203.0.113.8", "POST")[0], 200)
            for _ in range(20):
                self.assertEqual(self.request(name, "/api/auth/me")[0], 200)
            # Registration has its own allowance.
            self.assertEqual(self.request(name, "/api/auth/register", method="POST")[0], 200)

    def test_forged_headers_cannot_reset_rate_limit_on_direct_access(self):
        with self.container("192.0.2.10") as name:
            results = [self.request(name, "/api/auth/login", f"203.0.113.{n}", "POST")[0]
                       for n in range(1, 21)]
            self.assertIn(429, results)

    def test_registration_has_a_separate_limit(self):
        with self.container("127.0.0.1") as name:
            results = [self.request(name, "/api/auth/register", method="POST")[0] for _ in range(12)]
            self.assertIn(429, results)
            self.assertEqual(self.request(name, "/api/auth/login", method="POST")[0], 200)

    def test_backend_rate_limit_and_security_headers_are_preserved(self):
        with self.container("127.0.0.1") as name:
            status, headers, body = self.request(name, "/api/upstream-rate-limit")
            self.assertEqual(status, 429)
            self.assertEqual(json.loads(body), {"error": "upstream_rate_limited"})
            self.assertEqual(headers["Retry-After"], "42")
            for path in ("/", "/assets/missing.js", "/covers/missing.png", "/api/auth/me"):
                _, headers, _ = self.request(name, path)
                csp = headers["Content-Security-Policy"]
                self.assertIn("img-src 'self' https://s4.anilist.co data:", csp)
                self.assertIn("object-src 'none'", csp)
                self.assertEqual(headers["Permissions-Policy"], "camera=(), microphone=(), geolocation=()")


if __name__ == "__main__":
    unittest.main()
