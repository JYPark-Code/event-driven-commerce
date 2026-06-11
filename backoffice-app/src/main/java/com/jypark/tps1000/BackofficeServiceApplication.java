package com.jypark.tps1000;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** backoffice-service 부트스트랩 — 클래스패스에 backoffice 모듈만 있어 루트 패키지 스캔이 곧 서비스 경계다. */
@SpringBootApplication
public class BackofficeServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(BackofficeServiceApplication.class, args);
	}
}
