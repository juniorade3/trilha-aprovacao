.DEFAULT_GOAL := ajuda

ajuda:
	@printf '%s\n' 'Alvos: infra-subir, infra-parar, backend-executar, frontend-executar, testar-backend, testar-frontend, verificar-backend, verificar-frontend, verificar, limpar'

infra-subir:
	docker compose up -d

infra-parar:
	docker compose down

backend-executar:
	cd aplicativos/backend && ./mvnw spring-boot:run

frontend-executar:
	cd aplicativos/frontend && npm run dev

testar-backend:
	cd aplicativos/backend && ./mvnw test

testar-frontend:
	cd aplicativos/frontend && npm run test

verificar-backend:
	cd aplicativos/backend && ./mvnw verify

verificar-frontend:
	cd aplicativos/frontend && npm run verificar-tipos && npm run lint && npm run test && npm run build && npm run verificar-formatacao && npm audit

verificar: verificar-backend verificar-frontend

limpar:
	cd aplicativos/backend && ./mvnw clean
	cd aplicativos/frontend && rm -rf dist coverage
