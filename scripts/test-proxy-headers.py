#!/usr/bin/env python3
"""Exercise the production nginx template in an isolated, unpublished container."""

from pathlib import Path
import subprocess
import time
import unittest
import uuid


ROOT = Path(__file__).resolve().parents[1]
IMAGE = "nginx:1.31.4-alpine"


def docker(*args):
    return subprocess.run(
        ["docker", *args], check=True, capture_output=True, text=True, timeout=60
    ).stdout.strip()


class ProxyHeadersTest(unittest.TestCase):
    def exercise(self, trusted, expected_address, expected_proto, expected_host, xff):
        name = "mangashelf-proxy-test-" + uuid.uuid4().hex
        docker(
            "run", "--detach", "--name", name,
            "--add-host", "backend:127.0.0.1",
            "--env", "MS_TRUSTED_PROXY=" + trusted,
            "--env", "NGINX_ENVSUBST_FILTER=^MS_",
            "--volume", f"{ROOT / 'frontend/default.conf.template'}:/etc/nginx/templates/default.conf.template:ro",
            "--volume", f"{ROOT / 'scripts/tests/proxy-backend.conf'}:/etc/nginx/conf.d/backend.conf:ro",
            IMAGE,
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
            result = docker(
                "exec", name, "wget", "-qO-",
                "--header=X-Forwarded-For: " + xff,
                "--header=X-Forwarded-Proto: https",
                "--header=X-Forwarded-Host: forged.example",
                "--header=Forwarded: for=203.0.113.99;proto=https",
                "http://127.0.0.1/api/probe",
            )
            self.assertEqual(result, "|".join([
                expected_address, expected_address, expected_proto, expected_host, ""
            ]))
        finally:
            # Exact randomly generated test container; never a Compose service.
            docker("rm", "--force", name)

    def test_untrusted_client_cannot_supply_proxy_identity(self):
        self.exercise("192.0.2.10", "127.0.0.1", "http", "127.0.0.1",
                      "203.0.113.7, 10.0.0.8")

    def test_trusted_proxy_preserves_real_client_and_https(self):
        self.exercise("127.0.0.1", "203.0.113.7", "https", "forged.example",
                      "203.0.113.7")

    def test_private_hops_are_not_implicitly_trusted(self):
        self.exercise("127.0.0.1", "10.0.0.8", "https", "forged.example",
                      "203.0.113.7, 10.0.0.8")


if __name__ == "__main__":
    unittest.main()
