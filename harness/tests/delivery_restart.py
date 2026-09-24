"""在专用 MySQL 测试库上重启独立 Java 进程，核对闭环数据与会话恢复。

先运行 DeliveryFlowIT 和 mvn package；由环境传入 MYSQL_TEST_*，不读取演示库。
仅向模拟身份端点发送请求；日志存放在权限受限的临时目录。
"""
import json
import os
from pathlib import Path
import re
import secrets
import subprocess
import tempfile
import time
from urllib.parse import urlsplit
from urllib.request import Request, urlopen


def main():
    url = os.environ.get('MYSQL_TEST_URL', '')
    if not re.fullmatch(r'jdbc:mysql://[^/]+/task_hub_[a-zA-Z0-9_]*_test(?:\?.*)?', url):
        raise RuntimeError('必须显式指定专用 MYSQL_TEST_URL')
    target = urlsplit(url.removeprefix('jdbc:'))
    sql_env = {**os.environ, 'MYSQL_PWD': os.environ.get('MYSQL_TEST_PASSWORD', '')}

    def sql(query):
        result = subprocess.run(['mysql', '--batch', '--skip-column-names',
                                 '-h', target.hostname, '-P', str(target.port or 3306),
                                 '-u', os.environ.get('MYSQL_TEST_USER', 'taskhub'),
                                 target.path.lstrip('/'), '-e', query],
                                env=sql_env, capture_output=True, text=True)
        if result.returncode:
            raise RuntimeError('测试数据库查询失败；请核对连接配置')
        return result.stdout.strip()

    row = sql("SELECT e.phone,t.id,t.vehicle_id FROM delivery_order o "
              "JOIN employee e ON e.id=o.applicant_id JOIN batch_order bo ON bo.order_id=o.id "
              "JOIN vehicle_task t ON t.batch_id=bo.batch_id "
              "WHERE o.description='全链路验收物料' AND o.status='COMPLETED' "
              "AND t.state='FINISHED' ORDER BY o.created_at DESC LIMIT 1")
    if not row:
        raise RuntimeError('请先成功运行 DeliveryFlowIT')
    phone, task, vehicle = row.split('\t')
    # SQL 拼接只接受测试生成的 UUID。
    for value in (task, vehicle):
        if not re.fullmatch(r'[a-f0-9-]{36}', value):
            raise RuntimeError('测试标识格式不正确')
    root = Path(__file__).resolve().parents[2]
    jar = root / 'server/target/task-hub-server-0.0.1-SNAPSHOT.jar'
    env = {**os.environ, 'SPRING_PROFILES_ACTIVE': 'local',
           'SPRING_DATASOURCE_URL': url,
           'SPRING_DATASOURCE_USERNAME': os.environ.get('MYSQL_TEST_USER', 'taskhub'),
           'SPRING_DATASOURCE_PASSWORD': os.environ.get('MYSQL_TEST_PASSWORD', ''),
           'SIMULATOR_ENABLED': 'true', 'NOTIFICATIONS_ENABLED': 'false',
           'TASKHUB_AUTH_MOCK_ENABLED': 'true',
           'TASKHUB_AUTH_MOCK_SMS_CODE': f'{secrets.randbelow(1000000):06}',
           'TASKHUB_AUTH_SMS_HMAC_SECRET': secrets.token_hex(32),
           'TASKHUB_AUTH_MOCK_WECHAT_CODE': secrets.token_hex(16),
           'TASKHUB_AUTH_MOCK_WECHAT_OPENID': secrets.token_hex(16)}
    token = None
    base = ''

    def api(path, body=None, workspace='worker'):
        headers = {'Content-Type': 'application/json', 'X-Workspace': workspace}
        if token:
            headers['Authorization'] = 'Bearer ' + token
        request = Request(base + '/api' + path,
                          data=None if body is None else json.dumps(body).encode(), headers=headers)
        with urlopen(request, timeout=5) as response:
            data = json.load(response)
        assert data['code'] == 0
        return data['data']

    def command_count():
        return sql("SELECT COUNT(*) FROM simulator_command WHERE command_json LIKE '%" + vehicle + "%'")

    before_count = command_count()
    snapshots = []
    with tempfile.TemporaryDirectory(prefix='task-hub-restart-') as directory:
        for run in range(2):
            log_path = Path(directory) / f'server-{run}.log'
            with log_path.open('w') as log:
                process = subprocess.Popen(['java', '-jar', str(jar), '--server.address=127.0.0.1',
                                            '--server.port=0'], env=env, stdout=log, stderr=log)
                try:
                    deadline = time.monotonic() + 50
                    while time.monotonic() < deadline:
                        if process.poll() is not None:
                            raise RuntimeError('验收服务启动失败')
                        ports = re.findall(r'Tomcat started on port (\d+)', log_path.read_text())
                        if ports:
                            base = 'http://127.0.0.1:' + ports[-1]
                            break
                        time.sleep(0.2)
                    else:
                        raise RuntimeError('验收服务启动超时')
                    if run == 0:
                        challenge = api('/mini/auth/sms', {'phone': phone, 'purpose': 'LOGIN'})
                        session = api('/mini/auth/verify', {'phone': phone,
                                      'challengeId': challenge['challengeId'],
                                      'code': env['TASKHUB_AUTH_MOCK_SMS_CODE']})
                        token = session['token']
                    identity = api('/identity/me')
                    task_data = api('/tasks/' + task, workspace='dispatch')
                    assert task_data['state'] == 'FINISHED'
                    snapshots.append((identity['id'], task_data))
                finally:
                    process.terminate()
                    try:
                        process.wait(timeout=15)
                    except subprocess.TimeoutExpired:
                        process.kill()
                        process.wait(timeout=5)
    assert snapshots[0] == snapshots[1], '重启后身份或任务发生非预期变化'
    assert before_count == command_count(), '重启重复产生模拟车辆命令'
    print(json.dumps({'restart': 'PASS', 'sameSession': 'PASS', 'finishedTask': 'PASS',
                      'noCommandReplay': 'PASS', 'mysqlVersion': sql('SELECT VERSION()')}, ensure_ascii=False))


if __name__ == '__main__':
    main()
