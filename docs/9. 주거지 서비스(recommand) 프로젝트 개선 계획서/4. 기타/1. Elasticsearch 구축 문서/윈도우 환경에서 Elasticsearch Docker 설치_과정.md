# Elasticsearch Docker 설치 가이드

## Docker와 WSL 2 아키텍처

### 1. Docker의 핵심 원리: 커널(Kernel) 공유

Docker 컨테이너화 기술의 핵심은 커널 공유 메커니즘에 있다.

- **가상머신(VM)**: VM은 하드웨어를 가상화하여 게스트 OS를 통째로 설치하는 방식이다. 호스트 시스템 내에 완전히 독립적인 운영체제 환경을 구성한다. 무겁고 부팅이 느리지만, 완벽한 격리를 제공한다.

- **Docker 컨테이너**: 컨테이너는 OS를 통째로 설치하지 않는다. 대신 호스트(Host) OS의 커널(Kernel)을 공유한다. 컨테이너 안에는 애플리케이션과 그것을 실행하는 데 필요한 라이브러리/바이너리만 포함된다. 이로 인해 극도로 가볍고 빠르게 시작할 수 있다.

커널 공유는 Docker 기술의 핵심 개념이다. 현재 사용되는 대부분의 Docker 이미지(Elasticsearch, Ubuntu, Nginx 등)는 Linux 커널 위에서 동작하도록 설계되었다. 따라서 이러한 Linux 컨테이너는 Windows 커널과 직접적으로 호환되지 않는다.

### 2. Windows에서 Linux 컨테이너를 실행하는 방법: WSL 2

Windows PC에서 Linux 컨테이너를 실행하려면 중간에 Linux 커널을 제공하는 계층이 필요하다.

#### 과거 방식 (Hyper-V)
초기 Docker Desktop은 Windows의 가상화 기술인 Hyper-V를 사용해 'Moby'라는 최소한의 Linux VM을 백그라운드에 생성하고, 그 위에서 Docker 컨테이너를 실행했다. 이 방식은 여전히 VM 기반이라 상대적으로 무겁고 Windows와 파일 시스템 연동이 느린 단점이 있었다.

#### 현재 방식 (WSL 2)
WSL 2(Windows Subsystem for Linux 2)는 단순한 호환성 계층이 아니라, Windows 내에서 직접 실행되는 실제 Linux 커널이다. Microsoft가 Windows에 깊숙이 통합시킨 기술이다. Docker Desktop은 이 WSL 2의 Linux 커널을 직접 사용하여 컨테이너를 실행한다. 별도의 VM을 구동하는 것보다 훨씬 가볍고, 빠르며, Windows 파일 시스템과의 통합도 우수하다.

이것이 Docker Desktop이 WSL 2를 기반으로 동작하는 이유다. Windows 환경에서 Linux 컨테이너를 가장 효율적으로 실행하기 위한 최적의 솔루션이기 때문이다.

### 3. 순수 Windows 위의 Docker: Windows 컨테이너

"Docker가 순수 Windows 위에서는 동작하지 않는가?"라는 질문에 대한 답은 "동작하지만, 일반적으로 사용하는 컨테이너와는 다르다"이다.

- Windows 컨테이너가 별도로 존재한다.
- 이 컨테이너들은 Linux 커널이 아닌 Windows 커널을 공유한다.
- 주로 IIS(웹 서버), MS SQL Server, .NET Framework와 같은 Windows 기반 애플리케이션을 컨테이너화하기 위해 사용된다.

결론적으로, Linux 컨테이너는 Linux 커널에서만, Windows 컨테이너는 Windows 커널에서만 동작한다. Docker Hub에서 접하는 대다수의 오픈소스 이미지는 Linux 컨테이너이므로, Windows 개발자에게 최상의 경험을 제공하기 위해 Docker Desktop은 WSL 2를 통해 Linux 환경을 기본으로 제공한다.

### 4. WSL 2의 메모리 관리 방식

#### 정적 할당 vs. 동적 할당

- **정적 할당 방식**: 전체 물리 메모리를 Windows와 Linux에 고정적으로 분할하여 할당하는 방식이다. 한 번 할당된 메모리는 해당 시스템이 사용하지 않아도 다른 시스템이 접근할 수 없다.

- **WSL 2의 동적 할당 방식**: 전체 물리 메모리를 필요에 따라 유연하게 할당하고 반환하는 방식이다. Linux(WSL 2)가 Docker 컨테이너 실행 등으로 메모리가 필요해지면 그때그때 필요한 만큼 메모리를 할당받는다. 작업이 완료되면 사용하지 않는 메모리를 다시 반환한다. Windows도 동일한 방식으로 동작한다.

#### WSL 2의 실제 메모리 관리 방식

Windows는 전체 물리 메모리를 관리하는 호스트 역할을 한다. WSL 2의 Linux 커널은 그 위에서 동작하는 게스트에 해당한다.

1. **시작 단계**: WSL 2가 처음 시작될 때, Linux 커널은 동작에 필요한 최소한의 메모리만 Windows로부터 할당받는다.

2. **사용량 증가**: 사용자가 WSL 2 환경에서 Docker로 Elasticsearch처럼 메모리를 많이 사용하는 프로그램을 실행하면, Linux 커널은 호스트인 Windows에게 추가 메모리를 요청한다. Windows는 여유가 있는 만큼 메모리를 할당한다. 이때 작업 관리자를 보면 `vmmem` 또는 `vmmemWSL` 프로세스의 메모리 사용량이 크게 증가하는 것을 확인할 수 있다.

3. **사용량 감소 및 반환**: 사용자가 Docker 컨테이너를 중지하는 등 Linux 내에서 메모리 사용이 줄어들면, WSL 2는 사용하지 않게 된 메모리를 자동으로 Windows에게 반환한다. 이를 통해 Windows는 다른 작업을 위해 해당 메모리를 다시 사용할 수 있다.

물리 메모리의 소유권이 영구적으로 이전되는 것이 아니다. Windows가 관리하는 메모리 풀을 WSL 2가 필요에 따라 유연하게 할당받고 반환하는 개념이다. 이 동적 방식 덕분에 리소스 낭비 없이 Windows와 Linux 환경이 효율적으로 공존할 수 있다.

## 0단계: 사전 준비

본격적인 설치에 앞서 다음 구성요소들이 반드시 설치 및 설정되어 있어야 한다.

### WSL 2 (Windows Subsystem for Linux 2)
최신 버전의 Docker Desktop은 WSL 2를 기반으로 동작한다.

- **설치 확인**: Windows PowerShell이나 명령 프롬프트를 관리자 권한으로 열고 `wsl -l -v`를 입력했을 때 버전이 2로 나오는지 확인한다.
- **설치**: 설치되어 있지 않다면, 동일한 터미널에서 `wsl --install` 명령어를 실행하고 컴퓨터를 재부팅한다.

### Docker Desktop for Windows
공식 홈페이지에서 Docker Desktop을 다운로드하여 설치한다. 설치 과정에서 "Use WSL 2 instead of Hyper-V" 옵션이 있다면 반드시 체크한다.

### Docker 리소스 할당
Elasticsearch는 최소 4GB 이상의 메모리를 권장한다. Docker Desktop 실행 후, 작업 표시줄의 Docker 아이콘을 우클릭하여 Settings 메뉴로 들어간다. Resources > Advanced 탭에서 Memory 슬라이더를 최소 4GB 이상으로 설정한다.

## 1단계: docker-compose.yml 파일 작성

Docker Compose는 Elasticsearch와 Kibana라는 2개의 애플리케이션(컨테이너)을 한 번에 정의하고 실행할 수 있게 하는 도구다.

### docker-compose.yml 파일: 컨테이너 오케스트레이션 설계도

이 파일은 설치하는 프로그램이 아니라, 여러 컨테이너를 어떻게 실행하고 연결할지에 대한 설계도 또는 레시피가 기록된 텍스트 파일이다. `docker-compose up`이라는 명령어가 이 yml 파일을 읽고, 그 내용에 따라 여러 컨테이너들을 조화롭게 실행시키는 메커니즘이다.

이 설계도에는 다음과 같은 정보가 포함된다:
- **어떤 컨테이너를 실행할지**: Elasticsearch, Kibana 등
- **각 컨테이너의 설정은 어떻게 할지**: 환경 변수, 메모리 설정 등
- **어떤 포트를 개방할지**: 외부에서 접속할 수 있는 포트 (예: `9200`, `5601`)
- **컨테이너 간의 실행 순서는 어떻게 할지**: Kibana는 Elasticsearch가 실행된 후에 시작하도록 의존성 설정

### 파일 위치: 프로젝트의 최상단

이 파일은 특정 시스템 폴더에 설치하는 것이 아니라, 관리하고자 하는 프로젝트의 기준이 되는 폴더에 직접 생성한다. 예를 들어, `C:\` 드라이브에 `wherehouse-es-project`라는 폴더를 만들고 그 안에서 작업을 한다면, 파일 경로는 다음과 같다:

```
C:\wherehouse-es-project\      <-- 프로젝트 폴더
└── docker-compose.yml        <-- 바로 이 위치에 파일을 생성
```

이렇게 프로젝트 최상단에 두는 이유는, 터미널에서 해당 폴더로 이동한 뒤 `docker-compose up` 명령어를 실행했을 때 Docker가 현재 위치에서 `docker-compose.yml` 파일을 찾아 그 내용을 읽기 때문이다.

### 파일 작성 절차

1. 프로젝트를 진행할 폴더를 하나 만든다 (예: `C:\my-es-project`).
2. 해당 폴더 안에 메모장이나 코드 에디터를 사용하여 `docker-compose.yml`이라는 이름의 파일을 새로 만든다.
3. 아래 내용을 그대로 복사하여 `docker-compose.yml` 파일에 붙여넣고 저장한다.

### 완전한 docker-compose.yml 설정 (Package Registry 지원)

**WSL2 환경에서 Package Registry까지 정상 작동하는 완전한 설정:**

```yaml
version: '3.8'

services:
  # ===== ELASTICSEARCH 서비스 =====
  # Elasticsearch는 검색 및 분석 엔진입니다.
  # 모든 데이터를 저장하고 인덱싱하며, RESTful API를 제공합니다.
  elasticsearch:
    # 공식 Elastic 이미지 사용 (버전 8.14.1)
    image: docker.elastic.co/elasticsearch/elasticsearch:8.14.1
    
    # DNS 설정: 컨테이너가 외부 인터넷에 연결할 때 사용할 DNS 서버들
    # WSL2 + Docker 환경에서 DNS 해상도 문제를 해결하기 위해 필요
    # Package Registry(https://epr.elastic.co) 연결을 위해 반드시 필요
    dns:
      - 8.8.8.8    # Google의 기본 DNS 서버
      - 8.8.8.4    # Google의 보조 DNS 서버  
      - 1.1.1.1    # Cloudflare DNS 서버 (빠르고 안정적)
    
    # 컨테이너 이름을 명시적으로 지정
    # 다른 컨테이너에서 'wherehouse-es'로 이 컨테이너를 참조할 수 있음
    container_name: wherehouse-es
    
    # 호스트 네트워크 접근을 위한 설정
    # Docker 내부에서 Windows 호스트에 접근할 때 사용
    # WSL2 환경에서 네트워크 호환성을 개선하기 위해 필요
    extra_hosts:
      - "host.docker.internal:host-gateway"
    
    # Elasticsearch 환경 변수 설정
    environment:
      # 단일 노드 모드로 실행 (클러스터가 아닌 개발/학습용)
      # 프로덕션에서는 여러 노드를 사용하지만 학습용으로는 단일 노드면 충분
      - discovery.type=single-node
      
      # X-Pack 보안 기능 활성화
      # true로 설정하면 사용자 인증, 권한 관리, 암호화 등의 보안 기능 사용 가능
      # Package Registry 연결을 위해 필요함 (false로 설정하면 Fleet 기능 제한)
      - xpack.security.enabled=true
      
      # 사용자 등록 기능 활성화
      # 새 사용자를 Kibana에서 등록할 수 있게 해줌
      - xpack.security.enrollment.enabled=true
      
      # 라이선스 타입을 Basic으로 설정
      # Basic 라이선스는 무료이며 대부분의 기능 사용 가능
      - xpack.license.self_generated.type=basic
      
      # elastic 사용자(최고 관리자)의 비밀번호 설정
      # 브라우저에서 Kibana에 로그인할 때 사용하는 계정
      # Username: elastic, Password: changeme123
      - ELASTIC_PASSWORD=changeme123
      
      # Java 힙 메모리 설정 (최소 1GB, 최대 1GB)
      # Elasticsearch는 Java로 구동되므로 힙 메모리 설정이 중요
      # 시스템 메모리의 절반 정도를 할당하는 것이 권장됨
      - ES_JAVA_OPTS=-Xms1g -Xmx1g
    
    # 포트 매핑: 호스트의 포트를 컨테이너 포트와 연결
    ports:
      - "9200:9200"  # HTTP API 포트 (브라우저에서 http://localhost:9200으로 접근 가능)
      - "9300:9300"  # 노드 간 통신 포트 (클러스터 내부 통신용)
    
    # 볼륨 마운트: 데이터 영속성을 위해 필요
    # 컨테이너가 삭제되어도 데이터가 보존됨
    volumes:
      - esdata:/usr/share/elasticsearch/data  # esdata 볼륨을 Elasticsearch 데이터 디렉토리에 마운트

  # ===== SETUP 컨테이너 =====
  # Elasticsearch 초기 설정을 자동화하는 임시 컨테이너
  # kibana_system 사용자의 비밀번호를 설정한 후 종료됨
  # 이 컨테이너가 없으면 Kibana에서 "FATAL Error: elasticsearch username forbidden" 오류 발생
  setup:
    # Elasticsearch와 동일한 이미지 사용 (curl, elasticsearch 명령어 사용 가능)
    image: docker.elastic.co/elasticsearch/elasticsearch:8.14.1
    
    container_name: setup
    
    # Elasticsearch 컨테이너가 먼저 시작된 후에 실행되도록 설정
    depends_on:
      - elasticsearch
    
    # setup 컨테이너에서 사용할 환경 변수
    environment:
      - ELASTIC_PASSWORD=changeme123  # elastic 사용자 비밀번호 (Elasticsearch와 동일해야 함)
    
    # setup 컨테이너가 실행할 명령어들
    # bash 스크립트로 여러 명령을 순차적으로 실행
    command: >
      bash -c '
        echo "Waiting for Elasticsearch availability";
        # Elasticsearch가 준비될 때까지 대기 (최대 60초씩 반복)
        # _cluster/health API로 클러스터 상태가 yellow 이상이 될 때까지 기다림
        until curl -s -X GET "elasticsearch:9200/_cluster/health?wait_for_status=yellow&timeout=60s" -u "elastic:changeme123"; do sleep 30; done;
        
        echo "Setting kibana_system password";
        # kibana_system 사용자의 비밀번호를 설정
        # 이 사용자는 Kibana가 Elasticsearch에 연결할 때 사용하는 전용 계정
        # elastic 사용자는 너무 높은 권한을 가지고 있어서 Kibana 시스템 연결용으로는 부적절
        # Kibana에서 직접 elastic 사용자를 사용하면 "superuser account forbidden" 오류 발생
        until curl -s -X POST "elasticsearch:9200/_security/user/kibana_system/_password" -u "elastic:changeme123" -H "Content-Type: application/json" -d "{\"password\":\"changeme123\"}" | grep -q "^{}"; do sleep 10; done;
        
        echo "All done!";
      '

  # ===== KIBANA 서비스 =====
  # Kibana는 Elasticsearch의 웹 인터페이스입니다.
  # 데이터 시각화, 대시보드 생성, 검색 인터페이스 등을 제공합니다.
  # Fleet 관리, Package Registry 연동, Integrations 기능도 제공합니다.
  kibana:
    # 공식 Kibana 이미지 사용 (Elasticsearch와 동일한 버전 사용 권장)
    image: docker.elastic.co/kibana/kibana:8.14.1
    
    container_name: wherehouse-kibana
    
    # Kibana도 외부 Package Registry(https://epr.elastic.co)에 연결해야 하므로 DNS 설정 필요
    # 이 설정이 없으면 "Kibana cannot connect to the Elastic Package Registry" 오류 발생
    dns:
      - 8.8.8.8    # Google DNS
      - 8.8.8.4    # Google DNS 보조
      - 1.1.1.1    # Cloudflare DNS
    
    # 호스트 네트워크 접근 설정 (WSL2 환경에서 네트워크 호환성 개선)
    extra_hosts:
      - "host.docker.internal:host-gateway"
    
    # Kibana 웹 인터페이스 포트
    # 브라우저에서 http://localhost:5601로 접근 가능
    ports:
      - "5601:5601"
    
    # Elasticsearch와 setup이 먼저 실행된 후에 Kibana가 시작되도록 설정
    # setup이 kibana_system 비밀번호를 설정해야 Kibana가 정상 작동함
    depends_on:
      - elasticsearch
      - setup
    
    # Kibana 환경 변수 설정
    environment:
      # Kibana가 연결할 Elasticsearch 주소
      # 'elasticsearch'는 Docker Compose 네트워크 내에서의 서비스 이름
      - ELASTICSEARCH_HOSTS=http://elasticsearch:9200
      
      # Kibana가 Elasticsearch에 연결할 때 사용할 사용자 계정
      # kibana_system은 Kibana 전용 시스템 계정 (elastic보다 제한된 권한)
      # 이 계정을 사용해야 Fleet, Package Registry 등 고급 기능이 정상 작동함
      - ELASTICSEARCH_USERNAME=kibana_system
      - ELASTICSEARCH_PASSWORD=changeme123
      
      # 암호화 키들: Kibana의 고급 기능들을 위해 필요
      # 각각 다른 32자리 랜덤 문자열이어야 함
      # 이 키들이 없으면 Fleet, 저장된 객체, 보고서 기능에서 오류 발생
      
      # 저장된 객체(대시보드, 시각화 등) 암호화용 키
      # Missing encryption key 오류를 방지함
      - XPACK_ENCRYPTEDSAVEDOBJECTS_ENCRYPTIONKEY=a7a6311933d3503b89bc2dbc36572c33a6c10925682e591bffcab6911c06786d
      
      # 보안 세션 및 쿠키 암호화용 키
      # 사용자 로그인 세션 보안을 위해 필요
      - XPACK_SECURITY_ENCRYPTIONKEY=b7b7311933d3503b89bc2dbc36572c33a6c10925682e591bffcab6911c06786d
      
      # 리포트 및 PDF 생성 시 사용하는 암호화 키
      # 보고서 생성 기능을 위해 필요
      - XPACK_REPORTING_ENCRYPTIONKEY=c8c8311933d3503b89bc2dbc36572c33a6c10925682e591bffcab6911c06786d
      
      # Kibana 서버의 공개 URL 설정
      # Fleet Agent들이 Kibana에 접속할 때 사용하는 주소
      # Integrations 및 Fleet 관리 기능을 위해 필요
      - SERVER_PUBLICBASEURL=http://localhost:5601

# ===== 볼륨 정의 =====
# Docker 볼륨은 컨테이너가 삭제되어도 데이터를 보존하는 영구 저장소입니다.
volumes:
  # Elasticsearch 데이터를 저장하는 볼륨
  # 인덱스, 문서, 설정 등 모든 Elasticsearch 데이터가 여기에 저장됨
  # docker-compose down을 해도 데이터가 보존됨
  # docker-compose down -v를 해야 볼륨도 함께 삭제됨
  esdata:

# ===== 사용법 =====
# 1. 실행: docker-compose up -d
# 2. 확인: 
#    - Elasticsearch: http://localhost:9200
#    - Kibana: http://localhost:5601 (Username: elastic, Password: changeme123)
# 3. 중지: docker-compose down
# 4. 완전 삭제: docker-compose down -v

# ===== 트러블슈팅 =====
# 1. "Kibana cannot connect to the Elastic Package Registry" 오류
#    → DNS 설정이 제대로 되어 있는지 확인
#    → docker-compose restart kibana 시도
#
# 2. "You do not have permission to access" 오류
#    → elastic/changeme123으로 로그인
#
# 3. "FATAL Error: elasticsearch username forbidden" 오류
#    → setup 컨테이너가 정상 실행되었는지 확인: docker-compose logs setup
```

**중요한 변경사항**:

1. **보안 기능 활성화**: `xpack.security.enabled=true`로 변경
2. **Setup 컨테이너 추가**: `kibana_system` 사용자 비밀번호 자동 설정
3. **DNS 설정 추가**: WSL2 환경에서 Package Registry 연결을 위해 필요
4. **암호화 키 설정**: Fleet, 저장된 객체, 보고서 기능을 위한 3개의 암호화 키
5. **올바른 사용자 구조**: Kibana가 `kibana_system` 계정으로 연결

### 간단한 설정 (보안 비활성화)

만약 Package Registry가 필요하지 않고 단순한 학습용으로만 사용한다면, 기존의 간단한 설정도 사용 가능:

```yaml
version: '3.8'

services:
  # Elasticsearch 서비스 정의
  elasticsearch:
    image: docker.elastic.co/elasticsearch/elasticsearch:8.14.1 # 사용할 Elasticsearch 버전
    container_name: wherehouse-es
    environment:
      - discovery.type=single-node  # 단일 노드로 실행하기 위한 필수 설정
      - xpack.security.enabled=false # 학습 목적으로 보안 기능 비활성화 (매우 중요)
      - ES_JAVA_OPTS=-Xms1g -Xmx1g    # Java 힙 메모리 1GB로 설정
    ports:
      - "9200:9200" # Elasticsearch API 포트
      - "9300:9300" # 노드 간 통신 포트
    volumes:
      - esdata:/usr/share/elasticsearch/data # 데이터 영속성을 위한 볼륨 마운트

  # Kibana 서비스 정의
  kibana:
    image: docker.elastic.co/kibana/kibana:8.14.1 # Elasticsearch와 동일한 버전 사용
    container_name: wherehouse-kibana
    ports:
      - "5601:5601" # Kibana 웹 인터페이스 포트
    depends_on:
      - elasticsearch # Elasticsearch가 먼저 실행되도록 설정
    environment:
      - ELASTICSEARCH_HOSTS=http://elasticsearch:9200 # Kibana가 연결할 Elasticsearch 주소

volumes:
  esdata: # 위에서 사용한 esdata 볼륨을 정의
```

**권장사항**: Package Registry와 Fleet 기능을 사용하려면 **완전한 설정**을, 단순한 검색과 분석만 필요하다면 **간단한 설정**을 사용하세요.

## 2단계: Elasticsearch 및 Kibana 실행

1. **PowerShell 또는 명령 프롬프트(CMD) 실행**: `Win + R`을 누르고 `powershell` 또는 `cmd`를 입력한다.

2. **폴더 이동**: `docker-compose.yml` 파일을 저장한 폴더로 이동한다.
   ```bash
   cd C:\my-es-project
   ```

3. **Docker Compose 실행**: 아래 명령어를 입력하여 Elasticsearch와 Kibana를 백그라운드에서 실행시킨다.
   ```bash
   docker-compose up -d
   ```
   
   `-d` 옵션은 'detached mode'를 의미하며, 컨테이너를 백그라운드에서 실행시켜 터미널을 계속 사용할 수 있게 한다. 처음 실행 시에는 이미지를 다운로드하므로 몇 분 정도 소요될 수 있다.

## 3단계: 실행 확인

### 컨테이너 상태 확인
터미널에 아래 명령어를 입력하여 컨테이너가 정상적으로 실행 중(Up 상태)인지 확인한다.

```bash
docker-compose ps
```

**완전한 설정을 사용한 경우**: `wherehouse-es`, `setup`, `wherehouse-kibana` 3개의 컨테이너가 보여야 함
**간단한 설정을 사용한 경우**: `wherehouse-es`, `wherehouse-kibana` 2개의 컨테이너가 보여야 함

### Elasticsearch 확인
웹 브라우저를 열고 주소창에 `http://localhost:9200`을 입력한다. 

**완전한 설정 (보안 활성화)**: 사용자명/비밀번호 입력 창이 나타남
- Username: `elastic`
- Password: `changeme123`

**간단한 설정 (보안 비활성화)**: 아래와 같이 Elasticsearch 정보가 담긴 JSON 응답이 바로 보임

```json
{
  "name" : "wherehouse-es",
  "cluster_name" : "docker-cluster",
  ...
}
```

### Kibana 확인
웹 브라우저를 열고 주소창에 `http://localhost:5601`을 입력한다. Kibana가 시작되고 Elasticsearch와 연결되는 데 1~3분 정도 걸릴 수 있다.

**완전한 설정**: 로그인 화면이 나타남
- Username: `elastic`
- Password: `changeme123`

로그인 후 Integrations 페이지에서 Package Registry가 정상 연결되었는지 확인 가능

**간단한 설정**: 로그인 없이 바로 Kibana 환영 화면이 나타남

## 4단계: 중지 및 데이터 초기화

### 중지
실행을 중지하려면 `docker-compose.yml` 파일이 있는 폴더에서 아래 명령어를 실행한다. 컨테이너는 중지 및 제거되지만 데이터(volume)는 유지된다.

```bash
docker-compose down
```

### 완전 삭제 (데이터 포함)
데이터를 포함하여 모든 것을 초기 상태로 되돌리고 싶다면 `-v` 옵션을 추가한다.

```bash
docker-compose down -v
```

### 재시작
```bash
# WSL 재시작이 필요한 경우 (DNS 문제 해결)
wsl --shutdown
# 몇 초 후 WSL 재시작하고 docker-compose 실행

# 특정 서비스만 재시작
docker-compose restart kibana
```

## 트러블슈팅

### 1. "Kibana cannot connect to the Elastic Package Registry" 오류

**원인**: WSL2 환경에서 DNS 해상도 문제
**해결 방법**:
1. 완전한 설정의 DNS 설정 확인
2. WSL 재시작: `wsl --shutdown` 후 재실행
3. 컨테이너 재시작: `docker-compose restart kibana`

### 2. "You do not have permission to access" 오류

**원인**: 보안이 활성화된 상태에서 로그인 필요
**해결 방법**: `elastic` / `changeme123`으로 로그인

### 3. "FATAL Error: elasticsearch username forbidden" 오류

**원인**: setup 컨테이너가 실행되지 않았거나 실패함
**해결 방법**:
```bash
# setup 컨테이너 로그 확인
docker-compose logs setup

# "All done!" 메시지가 없다면 전체 재시작
docker-compose down
docker-compose up -d
```

### 4. WSL2 메모리 부족 오류

**해결 방법**: `C:\Users\[사용자명]\.wslconfig` 파일 생성
```ini
[wsl2]
memory=4GB
processors=2
```
파일 저장 후 `wsl --shutdown` 실행

## Spring Boot 애플리케이션과 Elasticsearch 연동

Docker를 통해 독립적인 Elasticsearch 서버가 성공적으로 실행되었다. 다음 단계는 Spring Boot 애플리케이션이 이 외부 서버와 통신할 수 있도록 연결 정보를 설정하는 것이다.

### 기술적 배경: Spring Boot의 자동 구성(Autoconfiguration)

Spring Boot의 핵심 기능 중 하나는 자동 구성(Autoconfiguration)이다. 개발자가 복잡한 설정 코드를 직접 작성하지 않아도, Spring Boot는 특정 조건이 충족되면 필요한 Bean들을 자동으로 생성하고 설정한다.

1. **의존성 감지**: 프로젝트의 `pom.xml`이나 `build.gradle`에 `spring-boot-starter-data-elasticsearch` 의존성이 포함되어 있으면, Spring Boot는 해당 프로젝트가 Elasticsearch를 사용한다고 인지한다.

2. **설정 파일 탐색**: 그 후, `application.yml` 파일에서 `spring.elasticsearch` 경로의 설정값이 있는지 탐색한다.

3. **자동 Bean 생성**: 해당 설정이 존재한다면, Spring Boot는 그 정보를 바탕으로 Elasticsearch와의 통신에 필요한 핵심 객체들(예: `ElasticsearchClient`)을 자동으로 생성하여 스프링 컨테이너에 등록(Bean으로 관리)한다.

따라서 `application.yml`에 설정을 추가하는 것은 이 자동 구성 메커니즘을 활성화하고 필요한 연결 정보를 제공하는 기술적 근거를 갖는다.

### build.gradle 의존성 추가

Elasticsearch를 연동하려면 build.gradle 파일을 수정해야 한다. application.yml에 설정을 추가하는 것이 애플리케이션에게 "어디에 접속해라"라고 알려주는 것이라면, build.gradle을 수정하는 것은 "접속할 때 필요한 도구(라이브러리)를 준비하라"라고 지시하는 것이다.

#### 수정이 필요한 기술적 근거

현재 build.gradle 파일에는 Spring Boot가 Redis(`spring-boot-starter-data-redis`)나 JPA(`spring-boot-starter-data-jpa`)와 통신하는 데 필요한 라이브러리들은 포함되어 있지만, Elasticsearch와 통신하는 데 필요한 라이브러리는 포함되어 있지 않다.

build.gradle의 dependencies 블록은 프로젝트를 빌드하고 실행하는 데 필요한 외부 라이브러리(JAR 파일)들의 목록이다. 여기에 `spring-boot-starter-data-elasticsearch`를 추가해야만, Spring Boot가 Elasticsearch와 통신하는 데 필요한 모든 클래스들(예: ElasticsearchClient, ElasticsearchRepository 인터페이스, 관련 자동 구성 로직 등)을 프로젝트에 포함시키게 된다.

이 의존성이 없으면, application.yml에 Elasticsearch 관련 설정을 아무리 추가해도 애플리케이션은 그 설정을 해석하고 사용할 코드를 가지고 있지 않으므로 아무런 효과가 없다.

#### 수정 방법

build.gradle 파일의 `dependencies { ... }` 블록 안에 다음 한 줄을 추가한다. 다른 `spring-boot-starter-data-*` 의존성 근처에 추가하면 가독성에 좋다.

```groovy
dependencies {
	implementation 'org.springframework.boot:spring-boot-starter-data-jpa'
    // ... (기존의 다른 의존성들)
	/* redis 의존성 추가 */
	implementation 'org.springframework.boot:spring-boot-starter-data-redis'
	// --- 이 아래에 Elasticsearch 의존성을 추가한다. ---
	implementation 'org.springframework.boot:spring-boot-starter-data-elasticsearch'
	// 외장 톰켓 애플리케이션 실행에 필요한 의존성 : war 배포 안할 시 꺼둘 것
	//providedRuntime 'org.springframework.boot:spring-boot-starter-tomcat'
	
    // ... (이하 생략)
}
```

#### 수정 후 필수 작업

1. build.gradle 파일을 저장한다.
2. IDE(STS4, IntelliJ 등)의 Gradle 탭에서 'Reload Gradle Project' (또는 새로고침 아이콘)를 클릭하여 프로젝트를 새로고침한다.

이 과정을 거쳐야 Gradle이 변경된 build.gradle 파일을 읽고, 새로 추가된 `spring-boot-starter-data-elasticsearch` 라이브러리를 인터넷(Maven Central)에서 다운로드하여 프로젝트의 클래스패스에 추가한다. 이 작업이 완료되어야 Java 코드에서 Elasticsearch 관련 기능을 사용할 수 있다.

### application.yml 설정 추가

`src/main/resources/application.yml` 파일을 열고, `spring:` 하위 레벨에 다음 내용을 추가한다. `jpa`, `redis` 등 다른 항목들과 동일한 들여쓰기 레벨을 유지해야 한다.

현재 application.yml 파일 예시를 보면 다음과 같은 구조로 되어 있다:

```yaml
spring:
  application:
    name: wherehouse
  
  datasource:
    driver-class-name: oracle.jdbc.OracleDriver
    # ... 기존 설정들

  data:
    redis:
      host: 43.202.178.156
      port: 6379
      # ... 기존 Redis 설정들

  jpa:
    hibernate:
      ddl-auto: none
      # ... 기존 JPA 설정들
```

이 구조에서 `data:` 섹션 하위에 `redis:` 설정이 있는 것처럼, **동일한 `data:` 섹션 아래에 `elasticsearch:` 설정을 추가**해야 한다:

#### 보안 비활성화 설정 (간단한 docker-compose 사용 시)

```yaml
spring:
  application:
    name: wherehouse
  
  datasource:
    driver-class-name: oracle.jdbc.OracleDriver
    url: jdbc:oracle:thin:@127.0.0.1:1521:xe
    username: SCOTT
    password: tiger
    # ... 기존 datasource 설정들

  data:
    redis:
      host: 43.202.178.156
      port: 6379
      timeout: 0
      lettuce:
        pool:
          max-active: 100
          max-idle: 50
          min-idle: 10
          max-wait: -1ms
          time-between-eviction-runs: 10s
    
    # Elasticsearch 설정을 data 섹션 하위에 추가
    elasticsearch:
      repositories:
        enabled: true  # Spring Data Elasticsearch 리포지토리 기능 활성화

  # Elasticsearch 연결 설정은 spring 최상위 레벨에 직접 추가
  elasticsearch:
    # uris: Spring Boot에 연결할 Elasticsearch 서버의 주소를 설정한다.
    #       'localhost:9200'은 현재 PC에서 실행 중인 Docker 컨테이너의 9200번 포트를 가리킨다.
    uris: http://localhost:9200
    
    # connection-timeout: 애플리케이션이 Elasticsearch 서버에 연결을 시도할 때,
    #                     응답이 없으면 최대 몇 초까지 기다릴지를 설정한다. 
    #                     네트워크 문제 발생 시 무한정 대기하는 것을 방지한다.
    connection-timeout: 10s
    
    # socket-timeout: 일단 연결이 성공한 후, 데이터를 주고받는 과정에서
    #                 서버의 응답이 없을 때 최대 몇 초까지 기다릴지 설정한다.
    #                 느린 쿼리나 서버 과부하로부터 애플리케이션을 보호한다.
    socket-timeout: 5s

  jpa:
    hibernate:
      ddl-auto: none
      # ... 기존 JPA 설정들
```

#### 보안 활성화 설정 (완전한 docker-compose 사용 시)

```yaml
spring:
  application:
    name: wherehouse
  
  datasource:
    driver-class-name: oracle.jdbc.OracleDriver
    url: jdbc:oracle:thin:@127.0.0.1:1521:xe
    username: SCOTT
    password: tiger
    # ... 기존 datasource 설정들

  data:
    redis:
      host: 43.202.178.156
      port: 6379
      timeout: 0
      lettuce:
        pool:
          max-active: 100
          max-idle: 50
          min-idle: 10
          max-wait: -1ms
          time-between-eviction-runs: 10s
    
    # Elasticsearch 설정을 data 섹션 하위에 추가
    elasticsearch:
      repositories:
        enabled: true  # Spring Data Elasticsearch 리포지토리 기능 활성화

  # Elasticsearch 연결 설정 (보안 활성화)
  elasticsearch:
    uris: http://localhost:9200
    username: elastic          # Docker의 ELASTIC_PASSWORD와 연결
    password: changeme123      # docker-compose.yml에서 설정한 비밀번호
    connection-timeout: 10s
    socket-timeout: 5s

  jpa:
    hibernate:
      ddl-auto: none
      # ... 기존 JPA 설정들
```

**중요한 설정 구조 설명**:

1. **`spring.data.elasticsearch:`** - Spring Data Elasticsearch 리포지토리 활성화
2. **`spring.elasticsearch:`** - Elasticsearch 클라이언트 연결 설정
3. **보안 설정**: Docker에서 보안을 활성화했다면 username/password 추가 필요

이 설정을 추가하고 애플리케이션을 실행하면, Spring Boot는 Docker 위에서 실행 중인 Elasticsearch 서버와 통신할 준비를 완료한다.

## 기술적 동작 메커니즘

### docker-compose.yml의 기술적 동작 과정 (인프라 계층)

이 파일은 Docker Engine이 해석하고 실행하는 명령어 집합이다.

1. **파싱 (Parsing)**: 사용자가 터미널에서 `docker-compose up` 명령을 실행하면, Docker 클라이언트는 현재 디렉토리의 `docker-compose.yml` 파일을 읽고 YAML 구문을 파싱하여 실행 계획을 수립한다.

2. **네트워크 생성**: `services`에 정의된 컨테이너들이 서로 통신할 수 있도록 격리된 가상 이더넷 브리지(Virtual Ethernet Bridge) 네트워크를 생성한다. 이 덕분에 `kibana` 컨테이너는 `http://elasticsearch:9200`이라는 호스트 이름으로 `elasticsearch` 컨테이너를 찾을 수 있다. DNS가 이 가상 네트워크 내에서 동작하기 때문이다.

3. **이미지 확인 및 다운로드**: Docker 데몬은 `image: docker.elastic.co/elasticsearch/elasticsearch:8.14.1` 같은 지시어를 보고, 로컬 캐시에 해당 이미지가 있는지 확인한다. 없으면, 원격 이미지 저장소(Docker Hub)에서 이미지를 다운로드(pull)한다. 이미지는 컨테이너를 생성하기 위한 읽기 전용 템플릿이다.

4. **볼륨(Volume) 마운트**: `volumes: - esdata:/usr/share/elasticsearch/data` 설정에 따라, Docker는 호스트(Windows)의 파일 시스템에 `esdata`라는 관리 볼륨을 생성하고, 이를 컨테이너 내부의 `/usr/share/elasticsearch/data` 경로에 마운트한다. 이는 컨테이너가 삭제되어도 데이터는 호스트에 영속적으로 저장되도록 하는 I/O 바인딩이다.

5. **컨테이너 생성 및 실행**: 다운로드한 이미지를 기반으로 실제 컨테이너 인스턴스를 생성한다. 이때 `environment`에 정의된 환경 변수(예: `discovery.type=single-node`)들이 컨테이너 내부의 OS에 주입된다. Elasticsearch 프로세스는 이 환경 변수들을 읽어 자신의 실행 설정을 구성한다.

6. **포트 포워딩 (Port Forwarding)**: `ports: - "9200:9200"` 설정은 가장 핵심적인 연결 고리다. Docker 데몬은 호스트 OS의 네트워크 스택에 규칙을 추가하여, **호스트의 9200번 포트로 들어오는 모든 TCP 트래픽을 `elasticsearch` 컨테이너의 9200번 포트로 전달(forward)** 하도록 설정한다.

7. **WSL2 네트워킹 처리**: WSL2 환경에서는 DNS 해상도와 네트워크 연결에 특별한 처리가 필요하다. `dns` 설정과 `extra_hosts` 설정이 이를 해결한다.

이 모든 과정이 끝나면, Docker의 역할은 컨테이너의 생명주기를 관리하고 네트워크 규칙을 유지하는 것이다. **Docker는 Spring Boot의 존재 자체를 알지 못한다.**

### application.yml의 기술적 동작 과정 (애플리케이션 계층)

이 파일은 Spring Boot 프레임워크가 애플리케이션의 `ApplicationContext`를 초기화할 때 사용하는 설정 소스다.

1. **클래스패스 스캐닝 및 자동 구성 (Autoconfiguration)**: Spring Boot 애플리케이션이 시작되면, `@SpringBootApplication` 어노테이션에 의해 클래스패스 스캐닝이 시작된다. `spring-boot-starter-data-elasticsearch` 라이브러리가 클래스패스에 존재하면, `ElasticsearchDataAutoConfiguration`과 같은 자동 구성 클래스가 활성화된다.

2. **프로퍼티 바인딩 (Property Binding)**: 활성화된 자동 구성 클래스는 `application.yml` 파일에서 `spring.elasticsearch` 접두사를 가진 프로퍼티들을 찾는다. Spring은 이 YAML 파일을 파싱하여 `uris`, `connection-timeout` 등의 값을 해당 자동 구성 클래스의 설정 객체에 주입(바인딩)한다.

3. **빈(Bean) 생성 및 의존성 주입**: 자동 구성 클래스는 바인딩된 프로퍼티 값들을 사용하여 Elasticsearch 통신에 필요한 핵심 빈(Bean)들, 특히 `ElasticsearchClient`를 생성한다. 예를 들어, `uris` 값인 `http://localhost:9200`은 `ElasticsearchClient`가 접속해야 할 서버의 엔드포인트(Endpoint) 주소로 설정된다. 이 생성된 빈은 스프링의 `ApplicationContext`(IoC 컨테이너)에 등록된다.

4. **애플리케이션 로직 실행**: 개발자가 작성한 서비스나 리포지토리 클래스에서 `@Autowired` 등을 통해 `ElasticsearchClient` 빈을 주입받아 사용한다. `ElasticsearchRepository`를 사용했다면, Spring Data 프레임워크가 내부적으로 이 클라이언트를 사용하여 실제 쿼리를 실행한다.

**Spring Boot는 자신이 연결하려는 `localhost:9200`이 Docker 컨테이너인지, 물리 서버인지 전혀 알지 못한다.** 그저 표준 HTTP 프로토콜을 통해 주어진 네트워크 주소로 요청을 보낼 뿐이다.

### 유기적 동작 과정 요약

1. **[준비]** `docker-compose up` 명령어로 Docker가 호스트 PC의 `9200` 포트를 Elasticsearch 컨테이너로 연결하는 터널을 구성한다.

2. **[실행]** Spring Boot 앱이 시작되면서 `application.yml`을 읽고 `localhost:9200`으로 연결해야 한다는 것을 인지한다.

3. **[연결]** Spring 앱 내의 `ElasticsearchClient`가 `localhost:9200`으로 HTTP 요청을 전송한다.

4. **[전달]** 호스트 OS는 이 요청을 받고, Docker가 설정한 포트 포워딩 규칙에 따라 요청 패킷을 Elasticsearch 컨테이너로 전달한다.

5. **[응답]** Elasticsearch는 요청을 처리하고 응답을 전송하며, 이 응답은 다시 터널을 통해 Spring Boot 앱으로 반환된다.

이처럼 두 시스템은 **표준 네트워크 프로토콜(TCP/IP, HTTP)과 포트 포워딩**이라는 명확한 인터페이스를 통해 상호작용하며, 서로의 내부 구현에 대해서는 전혀 알 필요가 없는 **느슨한 결합(Loose Coupling)** 상태로 동작한다. 이것이 바로 이 아키텍처의 핵심이다.

## 성공 요인 분석

### WSL2 환경에서의 특별한 고려사항

1. **DNS 해상도 문제**: WSL2 환경에서는 Docker 컨테이너가 외부 인터넷에 연결할 때 DNS 해상도 문제가 발생할 수 있다. 이는 WSL2가 랜덤한 서브넷을 선택하는 설계로 인한 것이다.

2. **Package Registry 연결**: Kibana의 Package Registry(`https://epr.elastic.co`) 연결은 보안 기능과 DNS 설정이 모두 올바르게 구성되어야 가능하다.

3. **사용자 계정 구조**: Elasticsearch의 `elastic` 사용자를 Kibana 시스템 연결에 직접 사용하면 안 되며, 전용 `kibana_system` 계정을 사용해야 한다.

### 완전한 솔루션의 핵심 구성요소

1. **DNS 설정**: `dns` 항목으로 안정적인 DNS 서버 지정
2. **네트워크 호환성**: `extra_hosts`로 WSL2 네트워크 문제 해결
3. **보안 구조**: Setup 컨테이너로 올바른 사용자 계정 구조 자동 설정
4. **암호화 키**: Fleet, 저장된 객체, 보고서 기능을 위한 3개의 암호화 키
5. **실행 순서**: `depends_on`으로 컨테이너 간 의존성 관리

이러한 모든 요소가 조화롭게 작동해야 WSL2 환경에서 완전한 Elastic Stack을 구축할 수 있다.