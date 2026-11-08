import { useEffect, useState } from 'react'
import {
  getCurrentUser,
  logout,
  startTelegramLogin,
  verifyTelegramCode,
} from './api.js'
import './App.css'

const PENDING_LOGIN_KEY = 'backtest-journal.telegram-login'
const LINK_LIFETIME_MS = 10 * 60 * 1000

function loadPendingLogin() {
  try {
    const pending = JSON.parse(sessionStorage.getItem(PENDING_LOGIN_KEY))
    if (
      pending?.linkToken &&
      pending?.link &&
      Date.now() - pending.createdAt < LINK_LIFETIME_MS
    ) {
      return pending
    }
    sessionStorage.removeItem(PENDING_LOGIN_KEY)
  } catch {
    sessionStorage.removeItem(PENDING_LOGIN_KEY)
  }
  return null
}

function savePendingLogin(pending) {
  try {
    if (pending) {
      sessionStorage.setItem(PENDING_LOGIN_KEY, JSON.stringify(pending))
    } else {
      sessionStorage.removeItem(PENDING_LOGIN_KEY)
    }
  } catch {
    // Login still works when browser storage is unavailable.
  }
}

function App() {
  const [pendingLogin, setPendingLogin] = useState(loadPendingLogin)
  const [user, setUser] = useState(null)
  const [code, setCode] = useState('')
  const [action, setAction] = useState('checking')
  const [error, setError] = useState('')

  useEffect(() => {
    let active = true

    getCurrentUser()
      .then((currentUser) => {
        if (!active) return
        setUser(currentUser)
        if (currentUser) {
          setPendingLogin(null)
          savePendingLogin(null)
        }
      })
      .catch((requestError) => {
        if (active) setError(requestError.message)
      })
      .finally(() => {
        if (active) setAction(null)
      })

    return () => {
      active = false
    }
  }, [])

  async function beginLogin() {
    setAction('starting')
    setError('')

    try {
      const login = await startTelegramLogin()
      const pending = { ...login, createdAt: Date.now() }
      setPendingLogin(pending)
      savePendingLogin(pending)
      setCode('')
    } catch (requestError) {
      setError(requestError.message)
    } finally {
      setAction(null)
    }
  }

  async function submitCode(event) {
    event.preventDefault()
    if (!pendingLogin || code.length !== 6) {
      setError('Enter the six-digit code from Telegram.')
      return
    }

    setAction('verifying')
    setError('')

    try {
      const signedInUser = await verifyTelegramCode(pendingLogin.linkToken, code)
      setUser(signedInUser)
      setPendingLogin(null)
      savePendingLogin(null)
      setCode('')
    } catch (requestError) {
      setError(requestError.message)
    } finally {
      setAction(null)
    }
  }

  function restartLogin() {
    setPendingLogin(null)
    savePendingLogin(null)
    setCode('')
    setError('')
  }

  async function signOut() {
    setAction('signing-out')
    setError('')

    try {
      await logout()
      setUser(null)
      restartLogin()
    } catch (requestError) {
      setError(requestError.message)
    } finally {
      setAction(null)
    }
  }

  const userName = user?.displayName || user?.telegramUsername || 'Trader'

  return (
    <main className="login-page">
      <section className="brand-panel" aria-label="Backtest Journal introduction">
        <header className="brand-header">
          <span className="brand-mark" aria-hidden="true">
            <span>B</span>
            <span>J</span>
          </span>
          <span className="brand-name">Backtest Journal</span>
        </header>

        <div className="brand-copy">
          <p className="eyebrow">A private record of your edge</p>
          <h1>
            Review the trade.
            <br />
            Refine the process.
          </h1>
          <p className="brand-description">
            Turn every backtest into a decision you can revisit, measure, and improve.
          </p>
        </div>

        <div className="market-visual" aria-hidden="true">
          <div className="visual-labels">
            <span>PROCESS / 01</span>
            <span>REVIEW WINDOW</span>
          </div>
          <svg viewBox="0 0 680 210" preserveAspectRatio="none">
            <defs>
              <linearGradient id="area-fill" x1="0" x2="0" y1="0" y2="1">
                <stop offset="0" stopColor="#d7ff4f" stopOpacity="0.22" />
                <stop offset="1" stopColor="#d7ff4f" stopOpacity="0" />
              </linearGradient>
            </defs>
            <path
              className="chart-area"
              d="M0 175 C42 169 62 146 101 151 C142 156 156 119 197 125 C240 131 251 93 291 101 C331 109 356 75 398 82 C442 90 461 47 503 59 C544 71 570 34 610 42 C638 48 657 25 680 18 L680 210 L0 210 Z"
            />
            <path
              className="chart-line"
              d="M0 175 C42 169 62 146 101 151 C142 156 156 119 197 125 C240 131 251 93 291 101 C331 109 356 75 398 82 C442 90 461 47 503 59 C544 71 570 34 610 42 C638 48 657 25 680 18"
            />
            <circle cx="503" cy="59" r="5" />
            <circle cx="680" cy="18" r="6" />
          </svg>
          <div className="visual-axis">
            <span>THESIS</span>
            <span>EXECUTION</span>
            <span>REVIEW</span>
          </div>
        </div>

        <footer className="brand-footer">
          <span><i /> Passwordless access</span>
          <span>Built for deliberate practice</span>
        </footer>
      </section>

      <section className="auth-panel" aria-label="Sign in">
        <div className="mobile-brand">
          <span className="brand-mark" aria-hidden="true">
            <span>B</span>
            <span>J</span>
          </span>
          <span className="brand-name">Backtest Journal</span>
        </div>

        <div className="auth-card">
          {action === 'checking' ? (
            <div className="checking-state" role="status">
              <span className="loader" />
              <p>Checking your session</p>
            </div>
          ) : user ? (
            <div className="success-state">
              <div className="success-icon" aria-hidden="true">
                <svg viewBox="0 0 24 24">
                  <path d="m6.5 12.5 3.4 3.4 7.6-8" />
                </svg>
              </div>
              <p className="card-kicker">Access confirmed</p>
              <h2>Welcome back, {userName}.</h2>
              <p className="card-description">
                Your Telegram account is verified and this browser is securely signed in.
              </p>
              {user.telegramUsername && (
                <div className="account-chip">
                  <span className="telegram-glyph">T</span>
                  <span>@{user.telegramUsername}</span>
                  <strong>VERIFIED</strong>
                </div>
              )}
              <div className="next-step">
                <span>01</span>
                <div>
                  <strong>Login is ready</strong>
                  <p>Your journal workspace is the next build step.</p>
                </div>
              </div>
              <button
                className="secondary-button"
                type="button"
                onClick={signOut}
                disabled={action === 'signing-out'}
              >
                {action === 'signing-out' ? 'Signing out...' : 'Sign out'}
              </button>
            </div>
          ) : pendingLogin ? (
            <div className="code-state">
              <button className="back-button" type="button" onClick={restartLogin}>
                <span aria-hidden="true">&larr;</span> Start over
              </button>
              <p className="card-kicker">Two-step sign in</p>
              <h2>Check your Telegram.</h2>
              <p className="card-description">
                Open the private bot chat, tap <strong>Start</strong>, then enter the code it sends you.
              </p>

              <div className="step-track" aria-label="Login progress">
                <div className="step complete">
                  <span>1</span>
                  <p><strong>Connect</strong>Open Telegram</p>
                </div>
                <div className="step active">
                  <span>2</span>
                  <p><strong>Verify</strong>Enter your code</p>
                </div>
              </div>

              <a
                className="telegram-link"
                href={pendingLogin.link}
                target="_blank"
                rel="noreferrer"
              >
                <span className="telegram-glyph">T</span>
                Open Telegram
                <span aria-hidden="true">↗</span>
              </a>

              <form onSubmit={submitCode}>
                <label htmlFor="telegram-code">Verification code</label>
                <input
                  id="telegram-code"
                  className="code-input"
                  type="text"
                  value={code}
                  onChange={(event) => setCode(event.target.value.replace(/\D/g, '').slice(0, 6))}
                  inputMode="numeric"
                  autoComplete="one-time-code"
                  pattern="[0-9]{6}"
                  maxLength="6"
                  placeholder="000000"
                  aria-describedby="code-hint"
                  autoFocus
                />
                <p id="code-hint" className="field-hint">The code expires after five minutes.</p>
                <button
                  className="primary-button"
                  type="submit"
                  disabled={action === 'verifying' || code.length !== 6}
                >
                  {action === 'verifying' ? <><span className="button-loader" /> Verifying</> : 'Verify and sign in'}
                </button>
              </form>
            </div>
          ) : (
            <div className="start-state">
              <p className="card-kicker">Secure journal access</p>
              <h2>Sign in without another password.</h2>
              <p className="card-description">
                We use Telegram to confirm it is you. No password to remember, reset, or expose.
              </p>

              <button
                className="primary-button telegram-button"
                type="button"
                onClick={beginLogin}
                disabled={action === 'starting'}
              >
                {action === 'starting' ? (
                  <><span className="button-loader" /> Preparing Telegram</>
                ) : (
                  <><span className="telegram-glyph">T</span> Continue with Telegram <span aria-hidden="true">→</span></>
                )}
              </button>

              <div className="login-explainer">
                <div><span>01</span><p><strong>Open the bot</strong>A private Telegram chat</p></div>
                <div><span>02</span><p><strong>Enter your code</strong>Valid for five minutes</p></div>
              </div>

              <p className="privacy-note">
                <svg viewBox="0 0 24 24" aria-hidden="true">
                  <rect x="5" y="10" width="14" height="10" rx="2" />
                  <path d="M8 10V7a4 4 0 0 1 8 0v3" />
                </svg>
                Your code is single-use. We never ask for access to your messages.
              </p>
            </div>
          )}

          {error && <p className="error-message" role="alert">{error}</p>}
        </div>

        <p className="auth-footer">Private by default <span>•</span> One-time verification</p>
      </section>
    </main>
  )
}

export default App
