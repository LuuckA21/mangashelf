#!/usr/bin/env python3
"""Verify the patched database with fresh and existing isolated volumes."""

from pathlib import Path
import subprocess
import sys
import time
import uuid


ROOT = Path(__file__).resolve().parents[1]


def docker(*args):
    return subprocess.run(
        ["docker", *args], check=True, capture_output=True, text=True, timeout=120
    ).stdout.strip()


def main(image):
    original = next(
        line.split()[1]
        for line in (ROOT / "db/Dockerfile").read_text().splitlines()
        if line.startswith("FROM ")
    )
    name = "mangashelf-db-test-" + uuid.uuid4().hex
    volume = name + "-data"
    password = uuid.uuid4().hex
    docker("volume", "create", volume)

    def sql(statement):
        return docker("exec", name, "psql", "-U", "test", "-d", "test",
                      "-v", "ON_ERROR_STOP=1", "-Atc", statement)

    def start(runtime):
        docker("run", "--detach", "--name", name,
               "--env", "POSTGRES_USER=test", "--env", "POSTGRES_DB=test",
               "--env", "POSTGRES_PASSWORD=" + password,
               "--volume", volume + ":/var/lib/postgresql", runtime)
        # TCP readiness avoids the temporary socket-only initdb server.
        for _ in range(60):
            try:
                docker("exec", name, "pg_isready", "-h", "127.0.0.1",
                       "-U", "test", "-d", "test")
                break
            except subprocess.CalledProcessError:
                time.sleep(0.5)
        else:
            raise RuntimeError("database not ready: " + docker("logs", name))
        assert sql("SHOW server_version_num").startswith("18")
        assert docker("exec", name, "stat", "-c", "%u", "/proc/1") == docker(
            "exec", name, "id", "-u", "postgres"
        ), "PostgreSQL must drop root privileges"

    try:
        start(original)
        sql("CREATE TABLE image_probe (value text NOT NULL); "
            "INSERT INTO image_probe VALUES ('preserved')")
        docker("stop", "--time", "30", name)
        docker("rm", name)
        start(image)
        assert sql("SELECT value FROM image_probe") == "preserved"
        sql("INSERT INTO image_probe VALUES ('patched')")
        assert sql("SELECT count(*) FROM image_probe") == "2"
        assert "image_probe" in docker(
            "exec", name, "pg_dump", "-U", "test", "-d", "test", "--schema-only"
        )
        docker("stop", "--time", "30", name)
        docker("rm", name)
        docker("volume", "rm", volume)
        docker("volume", "create", volume)
        start(image)
        assert sql("SELECT to_regclass('public.image_probe')") == ""
        sql("CREATE TABLE fresh_probe (id integer)")
        print("Database image: existing data, fresh initialization, non-root startup and dump passed.")
    finally:
        subprocess.run(["docker", "rm", "--force", name], capture_output=True, timeout=60)
        docker("volume", "rm", volume)


if __name__ == "__main__":
    main(sys.argv[1])
