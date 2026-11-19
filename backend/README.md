# Backend del asistente

Servidor TypeScript (Fastify) que guarda la clave de Anthropic, autentica a la app, limita el uso, ejecuta las herramientas de servidor (calculadora, búsqueda web) y envía las respuestas de Claude por streaming (SSE).

El servidor **no guarda estado**: la app envía el historial en cada petición. La memoria persistente llega en la etapa 3.

## Desarrollo local

```bash
cp .env.example .env      # rellena ANTHROPIC_API_KEY y APP_TOKENS (openssl rand -hex 32)
npm install
npm test                  # 36 tests
set -a; source .env; set +a
npm run dev
```

## API

### `GET /healthz`
Sin autenticación. Devuelve `{"ok":true}`.

### `POST /v1/chat`
Cabecera: `Authorization: Bearer <token de APP_TOKENS>`.

```json
{
  "messages": [{ "role": "user", "content": "¿Qué es el 15% de 80?" }],
  "clientTools": [
    { "name": "open_app", "description": "Abre una app instalada",
      "input_schema": { "type": "object", "properties": { "name": { "type": "string" } }, "required": ["name"] } }
  ],
  "context": { "now": "2026-11-19T09:30:00+01:00", "timezone": "Europe/Madrid", "assistantName": "opcional" }
}
```

El último mensaje debe ser del usuario. `clientTools` son las herramientas que ejecuta el móvil; sus nombres no pueden coincidir con los del servidor (`calculator`, `web_search`).

Respuesta: `text/event-stream` con estos eventos (`data` es JSON):

| Evento | Significado |
|---|---|
| `text` | `{delta}` fragmento de texto para pronunciar |
| `tool_status` | `{id,name,status}` una herramienta de servidor está `running`, `done` o `error` |
| `client_tool_calls` | Claude pide herramientas del teléfono. Contiene `calls`, `serverResults` y `newMessages` |
| `done` | Fin. Contiene `newMessages` para añadir al historial y `usage` |
| `error` | `{code,message}` con mensaje seguro para el usuario |

**Herramientas del teléfono:** al recibir `client_tool_calls`, la app añade `newMessages` a su historial, ejecuta cada llamada (pidiendo confirmación si es sensible) y envía una nueva petición cuyo último mensaje es `{"role":"user","content":[...serverResults, ...tool_result de cada llamada]}`. Cada `tool_result` es `{"type":"tool_result","tool_use_id":"<id>","content":"...","is_error":false}`. Todos los `tool_use` del turno necesitan su resultado.

## Despliegue en Cloud Run

Requisitos: proyecto de Google Cloud con facturación y la CLI `gcloud`.

```bash
PROJECT=tu-proyecto
REGION=europe-southwest1   # Madrid
gcloud config set project $PROJECT
gcloud services enable run.googleapis.com secretmanager.googleapis.com cloudbuild.googleapis.com

# Secretos (nunca en el código ni en variables en claro)
printf '%s' 'sk-ant-...'  | gcloud secrets create anthropic-api-key --data-file=-
openssl rand -hex 32 | tr -d '\n' | gcloud secrets create app-tokens --data-file=-
printf '%s' 'tu-clave-brave' | gcloud secrets create brave-api-key --data-file=-   # opcional

# Permiso para que Cloud Run lea los secretos
SA=$(gcloud projects describe $PROJECT --format='value(projectNumber)')-compute@developer.gserviceaccount.com
for s in anthropic-api-key app-tokens brave-api-key; do
  gcloud secrets add-iam-policy-binding $s --member=serviceAccount:$SA --role=roles/secretmanager.secretAccessor
done

# Desplegar desde este directorio
gcloud run deploy asistente-backend \
  --source . --region $REGION --allow-unauthenticated \
  --set-secrets=ANTHROPIC_API_KEY=anthropic-api-key:latest,APP_TOKENS=app-tokens:latest,BRAVE_API_KEY=brave-api-key:latest \
  --timeout=300 --max-instances=3
```

`--allow-unauthenticated` es necesario porque la autenticación la hace el propio servidor con el token Bearer. Para ver el token generado: `gcloud secrets versions access latest --secret=app-tokens`. Para rotarlo, crea una versión nueva del secreto y vuelve a desplegar.

Sin Brave, quita `BRAVE_API_KEY` de `--set-secrets`: la búsqueda web quedará desactivada y Claude no la verá.

## Limitaciones conocidas de esta etapa

- El token compartido es un control de acceso para uso personal. Cuando haya varios usuarios, se sustituye por Google Sign-In (etapa 3, junto con la base de datos).
- El límite de peticiones es en memoria por instancia; con `--max-instances=3` el límite efectivo puede triplicarse.
- La imagen Docker y el despliegue en Cloud Run no se han podido probar desde este entorno; el resto (tests, build y arranque del servidor compilado) sí.
