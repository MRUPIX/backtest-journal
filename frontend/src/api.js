let csrfTokenPromise

async function responseError(response) {
  let message = `Request failed (${response.status})`

  try {
    const body = await response.json()
    message = body.detail || body.message || message
  } catch {
    // Some security responses intentionally have no JSON body.
  }

  if (response.status === 503) {
    message = 'Telegram login is not configured on the server.'
  }

  return new Error(message)
}

function getCsrfToken() {
  if (!csrfTokenPromise) {
    csrfTokenPromise = fetch('/auth/csrf', {
      credentials: 'same-origin',
      headers: { Accept: 'application/json' },
    })
      .then(async (response) => {
        if (!response.ok) throw await responseError(response)
        const csrf = await response.json()
        return csrf.token
      })
      .catch((error) => {
        csrfTokenPromise = null
        throw error
      })
  }
  return csrfTokenPromise
}

async function request(path, options = {}, retryCsrf = true) {
  const method = options.method || 'GET'
  const unsafe = !['GET', 'HEAD', 'OPTIONS'].includes(method)
  const headers = { Accept: 'application/json', ...options.headers }

  if (options.body) headers['Content-Type'] = 'application/json'
  if (unsafe) headers['X-CSRF-TOKEN'] = await getCsrfToken()

  let response
  try {
    response = await fetch(path, {
      ...options,
      method,
      headers,
      credentials: 'same-origin',
    })
  } catch {
    throw new Error('Cannot reach the server. Make sure the backend is running.')
  }

  if (response.status === 401 && options.allowUnauthorized) return null
  if (response.status === 403 && unsafe) {
    csrfTokenPromise = null
    if (retryCsrf) return request(path, options, false)
  }
  if (!response.ok) throw await responseError(response)
  if (response.status === 204) return null
  return response.json()
}

export function getCurrentUser() {
  return request('/auth/me', { allowUnauthorized: true })
}

export function startTelegramLogin() {
  return request('/auth/telegram/start', { method: 'POST' })
}

export function verifyTelegramCode(linkToken, code) {
  return request('/auth/telegram/verify', {
    method: 'POST',
    body: JSON.stringify({ linkToken, code }),
  })
}

export async function logout() {
  await request('/auth/logout', { method: 'POST' })
  csrfTokenPromise = null
}
