# ResourceSwapHub development targets. Requires GNU Make.
# `make help` lists every target.

.DEFAULT_GOAL := help

DB_SCRIPT := "./ResourceSwapHub database/scripts/local-postgres.sh"

.PHONY: help
help:
	@echo 'ResourceSwapHub targets'
	@echo '  make db           start the local PostgreSQL server'
	@echo '  make db-stop      stop the local PostgreSQL server'
	@echo '  make db-reset     delete the local data directory (fresh start)'
	@echo '  make db-seed      apply reference + development demo seed data'
	@echo '  make api          run the Spring Boot API with Flyway migrations'
	@echo '  make web          run the Next.js dev server'
	@echo '  make test         run the backend test suite'
	@echo '  make compose      start docker compose (postgres + api + web)'

.PHONY: db
db:
	$(DB_SCRIPT) start

.PHONY: db-stop
db-stop:
	$(DB_SCRIPT) stop

.PHONY: db-reset
db-reset:
	$(DB_SCRIPT) reset

.PHONY: db-seed
db-seed:
	$(DB_SCRIPT) seed all

.PHONY: api
api:
	cd "ResourceSwapHub Backend" && if [ -f .env ]; then set -a; . ./.env; set +a; fi && ./mvnw spring-boot:run

.PHONY: web
web:
	cd "ResourceSwapHub FrontEnd" && npm run dev

.PHONY: test
test:
	cd "ResourceSwapHub Backend" && ./mvnw test

.PHONY: compose
compose:
	docker compose up -d
