.PHONY: install generate contract client frontend test build verify run dev-backend dev-frontend clean

install:
	cd contract && npm ci
	cd frontend && npm ci

contract:
	cd contract && npm run compile

client:
	cd frontend && npm run generate:api

generate: contract client

frontend:
	cd frontend && npm run build

test: contract
	cd backend && mvn test

build: generate frontend
	cd backend && mvn -DskipTests package

verify: generate frontend test

run:
	cd backend && mvn spring-boot:run

dev-backend:
	cd backend && mvn spring-boot:run

dev-frontend:
	cd frontend && npm run dev

clean:
	cd backend && mvn clean
	rm -rf frontend/dist
