#!/usr/bin/env python3
"""Exercise the backup contracts with real files/locks and fake Docker/Restic.

No network, real databases, credentials, user-home changes or cloud writes.
"""
import fcntl
import json
import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import time
import unittest

REPO = Path(__file__).resolve().parents[1]

FAKE_DOCKER = r'''#!/usr/bin/env python3
import io,json,os,sys,tarfile
from pathlib import Path
a=sys.argv[1:]
with open(os.environ['TEST_DOCKER_LOG'],'a') as f:f.write(json.dumps(a)+'\n')
if os.environ.get('TEST_DOCKER_FAIL')=='1':sys.exit(42)
s=' '.join(a)
if 'pg_dump' in s:
 if os.environ.get('TEST_CONFIG_CHANGE')=='1':
  with open('.env','a') as f:f.write('changed=yes\n')
 sys.stdout.write('test-db-dump')
elif 'tar -C /app/data/covers -czf - .' in s:
 with tarfile.open(fileobj=sys.stdout.buffer,mode='w|gz') as t:
  data=b'cover';info=tarfile.TarInfo('cover.webp');info.size=len(data);t.addfile(info,io.BytesIO(data))
elif 'tar -tzf -' in s:
 with tarfile.open(fileobj=sys.stdin.buffer,mode='r|gz') as t:
  for member in t:pass
elif a[:3]==['compose','images','-q']:print('sha256:'+'b'*64)
elif a[:2]==['image','inspect']:print('[]')
elif a[:4]==['compose','ps','-q','db']:print('production-db-id')
elif a[0]=='inspect':print('sha256:'+'b'*64)
elif a[0]=='run':
 assert a[a.index('--network')+1]=='none'
 assert 'type=volume,destination=/var/lib/postgresql' in a
 assert '--publish' not in a and '--volumes-from' not in a
 print('isolated-restore-id')
elif a[0]=='exec':
 assert 'isolated-restore-id' in a
 if 'pg_restore' in a:
  assert sys.stdin.read()=='test-db-dump'
  if os.environ.get('TEST_RESTORE_FAIL')=='1':sys.exit(4)
elif a[0]=='rm':assert a==['rm','-fv','isolated-restore-id']
'''

FAKE_RESTIC = r'''#!/usr/bin/env python3
import json,os,shutil,sys
from pathlib import Path
a=sys.argv[1:];root=Path(os.environ['TEST_CLOUD_STORE']);root.mkdir(exist_ok=True)
with open(os.environ['TEST_RESTIC_LOG'],'a') as f:f.write(json.dumps(a)+'\n')
if a[0]=='cat':sys.exit(0 if (root/'initialized').exists() else 1)
if a[0]=='init':(root/'initialized').touch()
elif a[0]=='backup':
 if os.environ.get('TEST_PARTIAL')=='1':sys.exit(3)
 if (root/'snapshot').exists():shutil.rmtree(root/'snapshot')
 shutil.copytree(a[1],root/'snapshot');(root/'source').write_text(a[1])
 print(json.dumps(dict(message_type='summary',snapshot_id='a'*64)))
elif a[0]=='check' and os.environ.get('TEST_CHECK_FAIL')=='1':sys.exit(5)
elif a[0]=='restore':
 target=Path(a[a.index('--target')+1]+(root/'source').read_text())
 shutil.copytree(root/'snapshot',target)
'''

FAKE_SYSTEMCTL = r'''#!/usr/bin/env python3
import os,subprocess,sys
from pathlib import Path
a=sys.argv[1:]
with open(os.environ['TEST_SYSTEMCTL_LOG'],'a') as f:f.write(' '.join(a)+'\n')
if 'show' in a and 'ActiveState' in a:print('inactive')
elif 'start' in a:
 subprocess.run(['bash',os.environ['TEST_PROJECT']+'/backup.sh'],check=True)
 if (Path(os.environ['MANGASHELF_SYSTEMD_USER_DIR'])/'mangashelf-backup.service.d/50-swiss-backup.conf').exists():
  subprocess.run(['bash',os.environ['TEST_PROJECT']+'/cloud-backup.sh'],check=True)
'''


class Backups(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory(prefix='mangashelf-backup-test-')
        self.addCleanup(self.tmp.cleanup)
        self.root = Path(self.tmp.name)
        self.project = self.root / 'project'
        self.backups = self.root / 'backups'
        self.config = self.root / 'restic'
        self.units = self.root / 'units'
        self.bin = self.root / 'bin'
        for p in (self.project / 'scripts', self.backups, self.config, self.bin, self.units, self.root / 'kutt'):
            p.mkdir(parents=True)
        for f in ('backup.sh', 'cloud-backup.sh', 'verify-backup.sh', 'install-backup.sh', 'scripts/backup-common.sh', 'scripts/backup.sh'):
            shutil.copy2(REPO / f, self.project / f)
        shutil.copytree(REPO / 'ops', self.project / 'ops')
        (self.project / '.env').write_text('SMTP_PASSWORD=TEST_SECRET_NOT_FOR_LOGS\n')
        (self.project / 'docker-compose.yml').write_text('services: {}\n')
        (self.config / 'password').write_text('TEST_RESTIC_SECRET')
        self.repository = 's3:https://s3.example.invalid/default/prd-apps-01/mangashelf'
        (self.config / 'repository').write_text(self.repository + '\n')
        (self.config / 'env.sh').write_text(
            f'export RESTIC_REPOSITORY={self.repository}\nexport RESTIC_PASSWORD_FILE={self.config}/password\n'
            'export AWS_ACCESS_KEY_ID=test-key\nexport AWS_SECRET_ACCESS_KEY=test-secret\n')
        (self.root / 'kutt/env.sh').write_text(
            'export RESTIC_REPOSITORY=s3:https://s3.example.invalid/default/prd-apps-01/kutt\n'
            'export AWS_ACCESS_KEY_ID=test-key\nexport AWS_SECRET_ACCESS_KEY=test-secret\n')
        for name, body in [('docker', FAKE_DOCKER), ('restic', FAKE_RESTIC), ('systemctl', FAKE_SYSTEMCTL),
                           ('id', '#!/bin/sh\nif [ "$1" = -un ]; then echo appsvc; else /usr/bin/id "$@"; fi\n'),
                           ('journalctl', '#!/bin/sh\nexit 0\n')]:
            p = self.bin / name; p.write_text(body); p.chmod(0o755)
        self.env = dict(os.environ, PATH=str(self.bin) + ':' + os.environ['PATH'],
                        MANGASHELF_BACKUP_ROOT=str(self.backups), MANGASHELF_RESTIC_CONFIG_DIR=str(self.config),
                        MANGASHELF_SYSTEMD_USER_DIR=str(self.units), MANGASHELF_KUTT_CONFIG_DIR=str(self.root / 'kutt'),
                        MANGASHELF_BACKUP_HOST='prd-apps-01', TEST_CLOUD_STORE=str(self.root / 'cloud'),
                        TEST_DOCKER_LOG=str(self.root / 'docker.log'), TEST_RESTIC_LOG=str(self.root / 'restic.log'),
                        TEST_SYSTEMCTL_LOG=str(self.root / 'systemctl.log'), TEST_PROJECT=str(self.project))

    def run_script(self, name, *args, ok=True, **env):
        result = subprocess.run(['bash', str(self.project / name), *args], env=dict(self.env, **env),
                                capture_output=True, text=True, timeout=20)
        if ok: self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
        else: self.assertNotEqual(result.returncode, 0, result.stdout + result.stderr)
        self.assertNotIn('TEST_SECRET_NOT_FOR_LOGS', result.stdout + result.stderr)
        self.assertNotIn('TEST_RESTIC_SECRET', result.stdout + result.stderr)
        return result

    def latest(self):
        return Path((self.backups / 'last-success').read_text().strip())

    def calls(self, tool):
        path = self.root / (tool + '.log')
        return [json.loads(line) for line in path.read_text().splitlines()] if path.exists() else []

    def old(self, name, marker=True):
        p = self.backups / name; p.mkdir()
        if marker: (p / '.managed-mangashelf-backup-v1').write_text('mangashelf-backup-v1\n')
        old = time.time() - 16 * 86400; os.utime(p, (old, old))
        return p

    def test_local_format_secrets_checksums_and_scoped_retention(self):
        obsolete = self.old('daily-20200101-040000-000000001')
        unmarked = self.old('daily-20200102-040000-000000002', marker=False)
        malformed = self.old('daily-unrelated')
        for legacy in ('daily', 'weekly', 'secrets'): (self.backups / legacy).mkdir()
        self.run_script('backup.sh')
        latest = self.latest()
        self.assertRegex(latest.name, r'^daily-\d{8}-\d{6}-\d{9}$')
        self.assertFalse(obsolete.exists()); self.assertTrue(unmarked.exists()); self.assertTrue(malformed.exists())
        for legacy in ('daily', 'weekly', 'secrets'): self.assertTrue((self.backups / legacy).is_dir())
        for file in ('.env', 'docker-compose.yml', 'database.dump', 'covers.tar.gz', 'image-ids.txt', 'image-digests.txt'):
            self.assertTrue((latest / file).is_file())
            self.assertEqual((latest / file).stat().st_mode & 0o077, 0)
        self.assertEqual(latest.stat().st_mode & 0o077, 0)
        subprocess.run(['sha256sum', '--strict', '-c', 'SHA256SUMS'], cwd=latest, check=True, capture_output=True)

    def test_failed_or_changing_backup_preserves_marker_and_old_copies(self):
        self.run_script('backup.sh'); saved = (self.backups / 'last-success').read_bytes()
        old = self.old('daily-20200101-040000-000000001')
        for env in ({'TEST_DOCKER_FAIL': '1'}, {'TEST_CONFIG_CHANGE': '1'}):
            self.run_script('backup.sh', ok=False, **env)
            self.assertEqual((self.backups / 'last-success').read_bytes(), saved)
            self.assertTrue(old.exists()); self.assertFalse(list(self.backups.glob('.partial-*')))

    def test_lock_prevents_overlapping_local_or_cloud_backup(self):
        with (self.backups / '.backup.lock').open('w') as lock:
            fcntl.flock(lock, fcntl.LOCK_EX | fcntl.LOCK_NB)
            self.run_script('backup.sh', ok=False)
        self.assertEqual(self.calls('docker'), [])

    def test_cloud_failures_do_not_report_success_or_prune_partial_upload(self):
        self.run_script('backup.sh'); self.run_script('cloud-backup.sh')
        saved = (self.backups / 'last-cloud-success.json').read_bytes()
        for env, expected in [({'TEST_PARTIAL': '1'}, ['backup']), ({'TEST_CHECK_FAIL': '1'}, ['backup', 'forget', 'check'])]:
            (self.root / 'restic.log').write_text('')
            self.run_script('cloud-backup.sh', ok=False, **env)
            self.assertEqual([a[0] for a in self.calls('restic')], expected)
            self.assertEqual((self.backups / 'last-cloud-success.json').read_bytes(), saved)
            self.assertFalse(list(self.backups.glob('.cloud-work-*')))
        calls = self.calls('restic'); forget = next(a for a in calls if a[0] == 'forget')
        self.assertIn('host,tags', forget); self.assertIn('mangashelf', forget)

    def test_wrong_repository_and_tampered_backup_block_cloud(self):
        self.run_script('backup.sh')
        config = self.config / 'env.sh'; original = config.read_text()
        config.write_text(original.replace('/mangashelf', '/kutt'))
        self.run_script('cloud-backup.sh', ok=False); self.assertEqual(self.calls('restic'), [])
        config.write_text(original)
        (self.latest() / 'database.dump').write_text('tampered')
        self.run_script('cloud-backup.sh', ok=False); self.assertEqual(self.calls('restic'), [])

    def test_cloud_restore_is_isolated_and_cleans_up_on_failure(self):
        self.run_script('backup.sh'); self.run_script('cloud-backup.sh'); self.run_script('verify-backup.sh')
        saved = (self.backups / 'last-restore-test.json').read_bytes()
        self.run_script('verify-backup.sh', ok=False, TEST_RESTORE_FAIL='1')
        self.assertEqual((self.backups / 'last-restore-test.json').read_bytes(), saved)
        self.assertFalse(list(self.backups.glob('.restore-test-*')))
        self.assertEqual(self.calls('docker')[-1], ['rm', '-fv', 'isolated-restore-id'])
        run = next(a for a in self.calls('docker') if a[0] == 'run')
        self.assertEqual(run[run.index('--network') + 1], 'none')

    def test_installer_migrates_old_units_without_changing_password_or_kutt(self):
        old_unit = '[Service]\nExecStart=/old/backup-with-cloud.sh\n'
        (self.units / 'mangashelf-backup.service').write_text(old_unit)
        dropin = self.units / 'mangashelf-backup.service.d'; dropin.mkdir()
        (dropin / '50-swiss-backup.conf').write_text('[Service]\nExecStart=/ops-local/backup-with-cloud.sh\n')
        (self.backups / 'daily').mkdir(); (self.backups / 'last-success').write_text('daily_backup=/old/path\n')
        key = (self.config / 'password').read_bytes(); kutt = (self.root / 'kutt/env.sh').read_bytes()
        # Also adopts the preceding standalone kit which had no repository guard file.
        (self.config / 'repository').unlink()
        self.run_script('install-backup.sh', '--cloud-from-kutt')
        archive = next(self.units.glob('mangashelf-backup-previous-*'))
        self.assertEqual((archive / 'mangashelf-backup.service').read_text(), old_unit)
        self.assertEqual((archive / 'last-success').read_text(), 'daily_backup=/old/path\n')
        self.assertIn('ExecStartPost=', (dropin / '50-swiss-backup.conf').read_text())
        self.assertIn('04:15:00 Europe/Zurich', (self.units / 'mangashelf-backup.timer').read_text())
        self.assertTrue((self.backups / 'daily').is_dir())
        self.assertEqual((self.config / 'password').read_bytes(), key)
        self.assertEqual((self.root / 'kutt/env.sh').read_bytes(), kutt)
        self.assertTrue((self.backups / 'last-cloud-success.json').exists())
        self.run_script('install-backup.sh', '--cloud-from-kutt')
        self.assertEqual((self.config / 'password').read_bytes(), key)

    def test_installer_creates_distinct_key_and_refuses_unknown_overrides(self):
        shutil.rmtree(self.config)
        self.run_script('install-backup.sh', '--cloud-from-kutt')
        self.assertTrue((self.config / 'password').stat().st_size > 32)
        self.assertEqual((self.config / 'password').stat().st_mode & 0o077, 0)
        unit = self.units / 'mangashelf-backup.service'; before = unit.read_bytes()
        (self.units / 'mangashelf-backup.service.d/90-custom.conf').write_text('[Service]\nNice=2\n')
        self.run_script('install-backup.sh', '--local', ok=False)
        self.assertEqual(unit.read_bytes(), before)


if __name__ == '__main__': unittest.main(verbosity=2)
