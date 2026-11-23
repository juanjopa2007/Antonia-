# Poner el servidor de Antonia en internet (desde el móvil)

Objetivo: que la app hable con Claude a través de tu propio servidor, sin que la clave de
Anthropic salga nunca de él. Tiempo aproximado: 30–45 minutos la primera vez.

Reglas de oro:
- **Nunca** pegues la clave de Anthropic ni el token en el chat, en GitHub ni en capturas.
  Solo van en Google Cloud (paso 3) y el token también en la app (paso 6).
- La suscripción de Claude (Pro/Max) **no** incluye la API: son cuentas y pagos distintos.

## 1. Clave de la API de Anthropic

1. Entra en https://console.anthropic.com e inicia sesión (o crea una cuenta).
2. **Billing** (facturación): añade saldo. Con 5 USD tienes de sobra para empezar.
3. Ahí mismo, pon un **límite de gasto mensual** bajo para que nunca haya sorpresas.
4. **API keys** → **Create key**, con el nombre `antonia`. Copia la clave (empieza por `sk-ant-`).
   Solo se muestra una vez: guárdala en tu gestor de contraseñas hasta el paso 3.

## 2. Cuenta de Google Cloud

1. Entra en https://console.cloud.google.com con tu cuenta de Google y acepta las condiciones.
2. Crea un proyecto: selector de proyectos (arriba) → **Proyecto nuevo** → nombre `antonia`.
3. Activa la facturación del proyecto (te pedirá una tarjeta). Cloud Run tiene una capa gratuita
   mensual amplia; para uso personal el coste suele ser mínimo. Puedes crear una alerta de
   presupuesto en **Facturación → Presupuestos y alertas**.

## 3. Crear el servicio en Cloud Run

1. En la barra de búsqueda de la consola escribe **Cloud Run** y ábrelo.
   Si te pide activar APIs (Cloud Run, Cloud Build, Artifact Registry, Secret Manager), acepta.
2. **Crear servicio** → elige **Implementar continuamente desde un repositorio**
   → **Configurar con Cloud Build**.
3. Proveedor **GitHub** → autoriza a Google Cloud Build → elige el repositorio
   `juanjopa2007/Antonia-` y la rama `main`.
4. Tipo de compilación: **Dockerfile**. Ubicación del origen: `/backend/Dockerfile`.
   Si te pregunta por el directorio de contexto, es `/backend`. Guarda.
5. Nombre del servicio: `antonia-backend`. Región: `europe-southwest1` (Madrid).
6. **Autenticación**: **Permitir acceso público**. Es correcto: el propio servidor exige el
   token en cada petición y rechaza todo lo demás.
7. Despliega **Contenedores, volúmenes, redes, seguridad** y en **Variables y secretos**:
   - **Hacer referencia a un secreto** → **Crear secreto nuevo** → nombre `anthropic-api-key`,
     valor: la clave del paso 1. Usa el secreto como **variable de entorno** con nombre
     `ANTHROPIC_API_KEY`. Si pide permiso para acceder al secreto, concédelo.
   - Otro secreto igual: nombre `app-tokens`, variable `APP_TOKENS`, valor: el token que
     te daré yo cuando llegues aquí (una cadena larga aleatoria, mínimo 24 caracteres).
8. En **Escalado**: número máximo de instancias `3` (mínimo `0`).
9. **Crear**. La primera compilación tarda unos minutos.
10. Al terminar verás una dirección `https://antonia-backend-….run.app`. Cópiala.

## 4. Comprobar que el servidor vive

Abre en el navegador del móvil `https://TU-DIRECCION/healthz`. Debe mostrar `{"ok":true}`.

## 5. Escribir los datos en la app

1. Abre Antonia → **Ajustes** → **Conexión con el servidor**.
2. Dirección: la del paso 3.10. Token: el mismo valor de `APP_TOKENS`.
3. **Guardar** y **Probar conexión**. Debe decir «Conexión correcta».
4. Vuelve al círculo, tócalo y habla. Antonia debería contestar con Claude.

## Si algo falla

| Síntoma | Causa probable |
|---|---|
| `/healthz` no carga | La compilación no ha terminado o falló: mira **Cloud Build → Historial**. |
| «El servidor no acepta el token» | El token de la app y el de `APP_TOKENS` no son idénticos (espacios, mayúsculas). |
| «No se ha podido conectar» | Dirección mal escrita o sin `https://`. |
| «El servidor ha devuelto un error» | Mira **Cloud Run → antonia-backend → Registros**; suele ser la clave de Anthropic sin saldo. |

## Cambiar o rotar el token

Cloud Run → `antonia-backend` → **Editar y desplegar nueva revisión** → cambia el secreto
`app-tokens` (nueva versión) → despliega, y pon el nuevo valor en los Ajustes de la app.
