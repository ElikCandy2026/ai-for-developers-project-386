.PHONY: install generate contract client check-generated frontend frontend-test frontend-boundary lint test build verify run dev-backend dev-frontend clean

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

frontend-test:
	cd frontend && npm test

frontend-boundary:
	@if grep -rn "api/generated" frontend/src --include='*.ts' --include='*.tsx' | grep -v '^frontend/src/data/' | grep -v '^frontend/src/api/generated/'; then \
		echo "UI must import from data/api, not from the generated SDK"; \
		exit 1; \
	fi

lint:
	cd frontend && npm run lint
	cd frontend && npm run typecheck

test:
	cd backend && mvn test

build: generate frontend
	cd backend && mvn -DskipTests clean package

verify: check-generated lint frontend frontend-test frontend-boundary test

run: build
	cd backend && mvn spring-boot:run

dev-backend:
	cd backend && mvn spring-boot:run

dev-frontend:
	cd frontend && npm run dev

clean:
	cd backend && mvn clean
	rm -rf frontend/dist
