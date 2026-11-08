.PHONY: install generate contract client check-generated frontend test build verify run dev-backend dev-frontend clean

GENERATED_PATHS = contract/tsp-output frontend/src/api/generated

install:
	cd contract && npm ci
	cd frontend && npm ci

contract:
	cd contract && npm run compile

client:
	cd frontend && npm run generate:api

generate: contract client

check-generated: generate
	@changed=$$(git status --porcelain -- $(GENERATED_PATHS)); \
	if [ -n "$$changed" ]; then \
		echo "Generated OpenAPI/SDK is out of date; commit the result of 'make generate':"; \
		echo "$$changed"; \
		exit 1; \
	fi

frontend:
	cd frontend && npm run build

test: contract
	cd backend && mvn test

build: generate frontend
	cd backend && mvn -DskipTests package

verify: check-generated frontend test

run: build
	cd backend && mvn spring-boot:run

dev-backend:
	cd backend && mvn spring-boot:run

dev-frontend:
	cd frontend && npm run dev

clean:
	cd backend && mvn clean
	rm -rf frontend/dist
