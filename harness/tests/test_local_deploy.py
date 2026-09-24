"""部署入口安全边界；不连接数据库，不停止真实服务。"""
import os
from pathlib import Path
import subprocess
import shutil
import socket
import tempfile
import unittest
import threading

ROOT = Path(__file__).resolve().parents[2]


class DeployTests(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory(prefix='task-hub-deploy-test-', dir='/tmp')
        self.addCleanup(self.tmp.cleanup)
        self.root = Path(self.tmp.name)
        self.envfile = self.root / 'config'
        self.envfile.write_text('')
        self.runtime = self.root / 'runtime'

    def run_cli(self, action, **values):
        env = dict(os.environ)
        env.update(TASKHUB_DEPLOY_ENV_FILE=str(self.envfile),
                   TASKHUB_DEPLOY_RUNTIME=str(self.runtime))
        env.update(values)
        args = action if isinstance(action, list) else [action]
        return subprocess.run(['bash', str(getattr(self, 'script', ROOT / 'deploy.sh')), *args],
                              env=env, text=True, capture_output=True, timeout=45)

    def force_fixture(self):
        sockets = [socket.socket() for _ in range(3)]
        for s in sockets:
            s.bind(('127.0.0.1', 0))
        ports = [s.getsockname()[1] for s in sockets]
        for s in sockets:
            s.close()
        self.envfile.write_text('MYSQL_DATABASE=task_hub_demo_test\nMYSQL_USER=root\n'
                               'TASKHUB_AUTH_MOCK_WECHAT_CODE=test\nTASKHUB_AUTH_MOCK_WECHAT_OPENID=test\n'
                               f'TASKHUB_DEPLOY_ADMIN_PORT={ports[0]}\nTASKHUB_DEPLOY_H5_PORT={ports[1]}\n'
                               f'TASKHUB_DEPLOY_SERVER_PORT={ports[2]}\n')
        # 只替换耗时构建与服务启动；预检、参数解析、真实进程终止逻辑不替换。
        self.script = self.root / 'deploy.sh'
        code = (ROOT / 'deploy.sh').read_text()
        code = code.replace('case "$action" in\n  deploy)',
                            'build_release() { release=unused; }\n'
                            'start_release() { stop_one backend; force_stop_port_occupants; }\n'
                            'case "$action" in\n  deploy)')
        self.script.write_text(code)

    def child(self, code):
        p = subprocess.Popen(['python3', '-u', '-c', code], stdout=subprocess.PIPE, text=True)
        self.addCleanup(p.stdout.close)
        self.addCleanup(lambda: p.kill() if p.poll() is None else None)
        self.assertEqual(p.stdout.readline().strip(), 'ready')
        threading.Thread(target=p.wait, daemon=True).start()
        return p

    def test_force_only_allows_deploy(self):
        for command in ('stop', 'restart', 'status', 'logs'):
            self.assertEqual(self.run_cli(['--force', command]).returncode, 2)

    def test_force_build_failure_keeps_backend_alive(self):
        self.force_fixture()
        self.script.write_text(self.script.read_text().replace(
            'build_release() { release=unused; }', 'build_release() { return 17; }'))
        p = self.child('import time; print("ready"); time.sleep(60)')
        (self.runtime / 'pids').mkdir(parents=True)
        (self.runtime / 'pids/backend.pid').write_text(str(p.pid))
        result = self.run_cli(['--force', 'deploy'])
        self.assertEqual(result.returncode, 17)
        self.assertIsNone(p.poll())

    def test_force_does_not_bypass_lock(self):
        (self.runtime / 'lock').mkdir(parents=True)
        result = self.run_cli(['--force', 'deploy'])
        self.assertNotEqual(result.returncode, 0)
        self.assertIn('锁', result.stderr)

    def test_force_nginx_preflight_failure_keeps_backend_alive(self):
        self.force_fixture()
        release = self.root / 'release'
        (release / 'admin').mkdir(parents=True)
        (release / 'h5').mkdir()
        for path in ('admin/index.html', 'h5/index.html', 'server.jar'):
            (release / path).touch()
        code = self.script.read_text().replace('build_release() { release=unused; }',
                                              f'build_release() {{ release="{release}"; }}')
        code = code.replace('start_release() { stop_one backend; force_stop_port_occupants; }',
                            'render() { return 19; }')
        self.script.write_text(code)
        p = self.child('import time; print("ready"); time.sleep(60)')
        (self.runtime / 'pids').mkdir(parents=True)
        (self.runtime / 'pids/backend.pid').write_text(str(p.pid))
        result = self.run_cli(['--force', 'deploy'])
        self.assertEqual(result.returncode, 19)
        self.assertIsNone(p.poll())

    def test_force_terminates_mismatched_backend_pid(self):
        self.force_fixture()
        p = self.child('import time; print("ready"); time.sleep(60)')
        (self.runtime / 'pids').mkdir(parents=True)
        (self.runtime / 'pids/backend.pid').write_text(str(p.pid))
        result = self.run_cli(['--force', 'deploy'])
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertIsNotNone(p.poll())

    def test_force_escalates_stubborn_backend_port_listener(self):
        self.force_fixture()
        with socket.socket() as s:
            s.bind(('127.0.0.1', 0))
            port = s.getsockname()[1]
        p = self.child(f'import socket,signal,time; signal.signal(signal.SIGTERM,signal.SIG_IGN); '
                       f's=socket.socket(); s.bind(("127.0.0.1",{port})); s.listen(); print("ready"); time.sleep(60)')
        result = self.run_cli(['--force'], TASKHUB_DEPLOY_SERVER_PORT=str(port))
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual(p.poll(), -9)

    def test_force_still_rejects_frontend_port_conflict(self):
        self.force_fixture()
        with socket.socket() as s:
            s.bind(('127.0.0.1', 0)); s.listen()
            result = self.run_cli(['--force', 'deploy'], TASKHUB_DEPLOY_ADMIN_PORT=str(s.getsockname()[1]))
            self.assertNotEqual(result.returncode, 0)
            self.assertIn('被其他进程占用', result.stderr)

    def test_unknown_action(self):
        self.assertEqual(self.run_cli('unknown').returncode, 2)

    def test_unsafe_runtime(self):
        result = self.run_cli('stop', TASKHUB_DEPLOY_RUNTIME='/tmp')
        self.assertNotEqual(result.returncode, 0)
        self.assertIn('运行目录', result.stderr)

    def test_invalid_port(self):
        result = self.run_cli('status', TASKHUB_DEPLOY_ADMIN_PORT='0')
        self.assertNotEqual(result.returncode, 0)
        self.assertIn('端口', result.stderr)

    def test_unknown_config_is_rejected(self):
        self.envfile.write_text('PATH=/not-allowed\n')
        result = self.run_cli('status')
        self.assertNotEqual(result.returncode, 0)
        self.assertIn('配置项', result.stderr)

    def test_config_is_never_executed(self):
        marker = self.root / 'executed'
        self.envfile.write_text(f'MYSQL_PASSWORD=$(touch {marker})\n')
        result = self.run_cli('status')
        self.assertIn('未运行', result.stdout)
        self.assertFalse(marker.exists())

    def test_lock_blocks_stop(self):
        (self.runtime / 'lock').mkdir(parents=True)
        result = self.run_cli('stop')
        self.assertNotEqual(result.returncode, 0)
        self.assertIn('锁', result.stderr)

    def test_forged_pid_does_not_stop_other_process(self):
        child = subprocess.Popen(['sleep', '30'])
        self.addCleanup(child.wait)
        self.addCleanup(child.terminate)
        (self.runtime / 'pids').mkdir(parents=True)
        (self.runtime / 'pids/backend.pid').write_text(str(child.pid))
        result = self.run_cli('stop')
        self.assertNotEqual(result.returncode, 0)
        self.assertIsNone(child.poll())
        self.assertIn('归属', result.stderr)

    def test_busy_port_does_not_kill_listener(self):
        listener = socket.socket()
        self.addCleanup(listener.close)
        listener.bind(('127.0.0.1', 0))
        listener.listen()
        self.envfile.write_text('MYSQL_DATABASE=task_hub_demo_test\nMYSQL_USER=root\n'
                               'TASKHUB_AUTH_MOCK_WECHAT_CODE=test\nTASKHUB_AUTH_MOCK_WECHAT_OPENID=test\n')
        result = self.run_cli('restart', TASKHUB_DEPLOY_ADMIN_PORT=str(listener.getsockname()[1]))
        self.assertIn('被其他进程占用', result.stderr)
        self.assertNotEqual(result.returncode, 0)
        self.assertGreater(listener.fileno(), -1)

    @unittest.skipUnless(shutil.which('nginx'), '需要本机 nginx')
    def test_real_nginx_master_with_padded_process_title_can_stop(self):
        root = self.runtime.resolve()
        (root / 'nginx').mkdir(parents=True)
        conf = root / 'nginx/nginx.conf'
        conf.write_text(f'pid "{root}/nginx/nginx.pid";\n'
                        f'error_log "{root}/nginx/error.log";\nevents {{}}\n')
        subprocess.run([shutil.which('nginx'), '-p', str(root / 'nginx')+'/', '-c', str(conf)], check=True)
        result = self.run_cli('stop')
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertFalse((root / 'nginx/nginx.pid').exists())

    @unittest.skipUnless(shutil.which('nginx'), '需要本机 nginx')
    def test_missing_pid_record_rejects_existing_managed_listener(self):
        root = self.runtime.resolve()
        (root / 'nginx').mkdir(parents=True)
        with socket.socket() as listener:
            listener.bind(('127.0.0.1', 0))
            port = listener.getsockname()[1]
        self.envfile.write_text('MYSQL_DATABASE=task_hub_demo_test\nMYSQL_USER=root\n'
                               'TASKHUB_AUTH_MOCK_WECHAT_CODE=test\nTASKHUB_AUTH_MOCK_WECHAT_OPENID=test\n')
        conf = root / 'nginx/nginx.conf'
        conf.write_text(f'pid "{root}/nginx/nginx.pid";\nerror_log "{root}/nginx/error.log";\n'
                        f'events {{}}\nhttp {{ server {{ listen 127.0.0.1:{port}; return 200; }} }}\n')
        subprocess.run([shutil.which('nginx'), '-p', str(root / 'nginx')+'/', '-c', str(conf)], check=True)
        pidfile = root / 'nginx/nginx.pid'
        saved = pidfile.read_text()
        try:
            pidfile.unlink()
            result = self.run_cli('restart', TASKHUB_DEPLOY_ADMIN_PORT=str(port))
            self.assertNotEqual(result.returncode, 0)
            self.assertIn('被其他进程占用', result.stderr)
        finally:
            pidfile.write_text(saved)
            self.assertEqual(self.run_cli('stop').returncode, 0)


if __name__ == '__main__':
    unittest.main()
