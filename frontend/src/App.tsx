import { useEffect, useMemo, useState } from 'react'
import type { FormEvent } from 'react'
import { api } from './api'
import type { Analysis, ProviderStatus, User, Verification } from './types'

type Page = 'dashboard' | 'new' | 'history' | 'profile' | 'result'

export default function App() {
  const [token, setToken] = useState(() => sessionStorage.getItem('springsentry-token') || '')
  const [user, setUser] = useState<User | null>(null)
  const [sessions, setSessions] = useState<Analysis[]>([])
  const [selected, setSelected] = useState<Analysis | null>(null)
  const [page, setPage] = useState<Page>('dashboard')
  const [authLoading, setAuthLoading] = useState(Boolean(token))
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const [verification, setVerification] = useState<Verification | null>(null)
  const [providerStatus, setProviderStatus] = useState<ProviderStatus | null>(null)
  const [checkingProvider, setCheckingProvider] = useState(false)

  useEffect(() => {
    if (!token) return
    api.me(token)
      .then(setUser)
      .catch(() => signOut())
      .finally(() => setAuthLoading(false))
  }, [token])

  useEffect(() => {
    if (!token || !user) return
    api.history(token).then(setSessions).catch((reason: Error) => setError(reason.message))
    void checkProviderStatus()
  }, [token, user])

  const stats = useMemo(() => ({
    total: sessions.length,
    ai: sessions.filter((session) => session.analysisSource === 'BHARATCODE').length,
    demo: sessions.filter((session) => session.analysisSource === 'DEMO').length,
  }), [sessions])

  function signOut() {
    sessionStorage.removeItem('springsentry-token')
    setToken('')
    setUser(null)
    setSessions([])
    setSelected(null)
    setPage('dashboard')
    setProviderStatus(null)
  }

  async function handleAuth(name: string, email: string, password: string, mode: 'login' | 'register') {
    setBusy(true)
    setError('')
    try {
      const result = mode === 'login'
        ? await api.login(email, password)
        : await api.register(name, email, password)
      if (!result.token) throw new Error('The server did not return a login token.')
      sessionStorage.setItem('springsentry-token', result.token)
      setToken(result.token)
      setUser(result)
      setPage('dashboard')
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : 'Could not sign in.')
    } finally {
      setBusy(false)
    }
  }

  async function submitAnalysis(data: { title: string; description: string; stackTrace: string; sourceCode: string }) {
    if (!token) return
    setBusy(true)
    setError('')
    try {
      const result = await api.analyze(token, data)
      setSelected(result)
      setSessions((current) => [result, ...current.filter((item) => item.id !== result.id)])
      setPage('result')
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : 'Analysis failed. Please try again.')
      void checkProviderStatus()
    } finally {
      setBusy(false)
    }
  }

  async function openAnalysis(id: string) {
    if (!token) return
    setBusy(true)
    setError('')
    try {
      const result = await api.getAnalysis(token, id)
      setSelected(result)
      setPage('result')
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : 'Could not open this analysis.')
    } finally {
      setBusy(false)
    }
  }

  async function runSampleVerification() {
    if (!token) return
    setBusy(true)
    setError('')
    try {
      setVerification(await api.verifySample(token))
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : 'Sample verification could not run.')
    } finally {
      setBusy(false)
    }
  }

  async function checkProviderStatus() {
    if (!token) return
    setCheckingProvider(true)
    try {
      setProviderStatus(await api.providerStatus(token))
    } catch {
      setProviderStatus({
        mode: 'AI_UNAVAILABLE',
        message: 'The backend provider status could not be checked.',
        checkedAt: new Date().toISOString(),
      })
    } finally {
      setCheckingProvider(false)
    }
  }

  if (authLoading) return <div className="center-screen"><div className="spinner" /><span>Loading your workspace…</span></div>
  if (!user || !token) return <AuthScreen onSubmit={handleAuth} busy={busy} error={error} clearError={() => setError('')} />

  return (
    <div className="app-shell">
      <aside className="sidebar">
        <button className="brand" onClick={() => setPage('dashboard')} aria-label="SpringSentry dashboard">
          <span className="brand-mark">S</span>
          <span><strong>SpringSentry</strong><small>AI DEBUG ASSISTANT</small></span>
        </button>
        <div className="nav-label">WORKSPACE</div>
        <nav className="nav-list" aria-label="Main navigation">
          <NavButton active={page === 'dashboard'} icon="⌂" label="Overview" onClick={() => setPage('dashboard')} />
          <NavButton active={page === 'new'} icon="＋" label="New analysis" onClick={() => { setError(''); setPage('new') }} />
          <NavButton active={page === 'history'} icon="◷" label="Analysis history" onClick={() => setPage('history')} />
        </nav>
        <div className="sidebar-bottom">
          <div className="local-card">
            <span className="status-dot" />
            <div><strong>Local workspace</strong><small>Private to this account</small></div>
          </div>
          <button className={`profile-link ${page === 'profile' ? 'selected' : ''}`} onClick={() => setPage('profile')}>
            <span className="avatar">{initials(user.name)}</span>
            <span className="profile-copy"><strong>{user.name}</strong><small>Account settings</small></span>
            <span className="profile-arrow">›</span>
          </button>
        </div>
      </aside>

      <main className="main-area">
        <header className="topbar">
          <div className="breadcrumb"><span>SpringSentry</span><span className="crumb-slash">/</span><strong>{pageTitle(page)}</strong></div>
          <div className="topbar-actions">
            <ProviderStatusBadge status={providerStatus} checking={checkingProvider} onRefresh={checkProviderStatus} />
            <button className="icon-button" onClick={signOut} title="Sign out" aria-label="Sign out">↪</button>
          </div>
        </header>

        <div className="content-wrap">
          {error && <div className="notice error-notice" role="alert"><span>!</span><p>{error}</p><button onClick={() => setError('')} aria-label="Dismiss">×</button></div>}
          {page === 'dashboard' && <Dashboard user={user} sessions={sessions} stats={stats} busy={busy} verification={verification} onNew={() => setPage('new')} onOpen={openAnalysis} onVerify={runSampleVerification} />}
          {page === 'new' && <NewAnalysis onSubmit={submitAnalysis} busy={busy} />}
          {page === 'history' && <History sessions={sessions} onOpen={openAnalysis} onNew={() => setPage('new')} />}
          {page === 'profile' && <Profile user={user} stats={stats} onSignOut={signOut} />}
          {page === 'result' && selected && <AnalysisDetail analysis={selected} onBack={() => setPage('history')} />}
        </div>
        <footer className="footer"><span>SpringSentry AI</span><span>AI suggestions are unverified until tests are actually run.</span></footer>
      </main>
    </div>
  )
}

function AuthScreen({ onSubmit, busy, error, clearError }: {
  onSubmit: (name: string, email: string, password: string, mode: 'login' | 'register') => Promise<void>
  busy: boolean
  error: string
  clearError: () => void
}) {
  const [mode, setMode] = useState<'login' | 'register'>('register')
  const [name, setName] = useState('')
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [showPassword, setShowPassword] = useState(false)

  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    clearError()
    void onSubmit(name, email, password, mode)
  }

  return (
    <div className="auth-shell">
      <section className="auth-story">
        <div className="auth-brand"><span className="brand-mark">S</span><strong>SpringSentry</strong></div>
        <div className="story-content">
          <span className="eyebrow light-eyebrow">A clearer path through the stack trace</span>
          <h1>Debug with<br /><em>understanding.</em></h1>
          <p>Turn Spring Boot errors into a structured diagnosis, a reviewable fix, and tests you can actually run.</p>
          <div className="story-points"><span><b>01</b> Read the evidence</span><span><b>02</b> Review a suggested fix</span><span><b>03</b> Verify with tests</span></div>
        </div>
        <div className="story-footnote">Built as an educational project · Java + React</div>
      </section>
      <section className="auth-panel">
        <form className="auth-form" onSubmit={submit}>
          <span className="eyebrow">YOUR WORKSPACE</span>
          <h2>{mode === 'register' ? 'Create your account' : 'Welcome back'}</h2>
          <p className="muted">{mode === 'register' ? 'Start a debugging session and keep your analysis history.' : 'Sign in to continue to your debugging workspace.'}</p>
          {error && <div className="notice error-notice auth-error" role="alert">{error}</div>}
          {mode === 'register' && <label className="field-label">Your name<input value={name} onChange={(event) => setName(event.target.value)} placeholder="Ashish Pagariya" autoComplete="name" minLength={2} maxLength={80} required /></label>}
          <label className="field-label">Email address<input type="email" value={email} onChange={(event) => setEmail(event.target.value)} placeholder="you@example.com" autoComplete="email" maxLength={160} required /></label>
          <label className="field-label">Password<span className="password-wrap"><input type={showPassword ? 'text' : 'password'} value={password} onChange={(event) => setPassword(event.target.value)} placeholder="At least 8 characters" autoComplete={mode === 'register' ? 'new-password' : 'current-password'} minLength={8} maxLength={72} required /><button type="button" className="show-password" onClick={() => setShowPassword(!showPassword)}>{showPassword ? 'Hide' : 'Show'}</button></span></label>
          <button className="button primary full-width auth-submit" type="submit" disabled={busy}>{busy ? <><span className="button-spinner" /> Working…</> : mode === 'register' ? 'Create account' : 'Sign in'}<span>→</span></button>
          <p className="auth-switch">{mode === 'register' ? 'Already have an account?' : 'New to SpringSentry?'} <button type="button" onClick={() => { setMode(mode === 'register' ? 'login' : 'register'); clearError() }}>{mode === 'register' ? 'Sign in' : 'Create an account'}</button></p>
          <div className="auth-security"><span>▣</span><p>Your password is hashed on the server. The BharatCode API key never reaches your browser.</p></div>
        </form>
      </section>
    </div>
  )
}

function Dashboard({ user, sessions, stats, busy, verification, onNew, onOpen, onVerify }: {
  user: User; sessions: Analysis[]; stats: { total: number; ai: number; demo: number }; busy: boolean
  verification: Verification | null; onNew: () => void; onOpen: (id: string) => void; onVerify: () => void
}) {
  const recent = sessions.slice(0, 4)
  return (
    <>
      <section className="welcome-row"><div><span className="eyebrow">YOUR WORKSPACE IS READY</span><h1>Good debugging, {user.name.split(' ')[0]}.</h1><p className="muted">Here’s what’s happening across your recent sessions.</p></div><button className="button primary" onClick={onNew}><span>＋</span> Start new analysis</button></section>
      <section className="metric-grid">
        <Metric label="Total analyses" value={stats.total} caption="Saved to your history" icon="⌕" tone="mint" />
        <Metric label="AI analyses" value={stats.ai} caption="BharatCode responses" icon="✳" tone="blue" />
        <Metric label="Demo analyses" value={stats.demo} caption="Local rule-based results" icon="◇" tone="peach" />
        <Metric label="Verified fixes" value="—" caption="No submitted fix verified yet" icon="✓" tone="lilac" />
      </section>
      <section className="dashboard-grid">
        <div className="panel recent-panel">
          <div className="panel-heading"><div><span className="eyebrow">YOUR WORK</span><h2>Recent analyses</h2></div><button className="text-button" onClick={() => onOpen(recent[0]?.id || '')} disabled={!recent.length}>View latest <span>→</span></button></div>
          {recent.length ? <div className="session-list">{recent.map((session) => <SessionRow key={session.id} session={session} onClick={() => onOpen(session.id)} />)}</div> : <EmptyState title="No analyses yet" text="Paste a stack trace to get your first diagnosis." action="Start your first analysis" onAction={onNew} />}
        </div>
        <div className="panel verification-panel">
          <div className="panel-heading"><div><span className="eyebrow">CONTROLLED TEST RUN</span><h2>Sample project</h2></div><span className="sample-icon">⌘</span></div>
          <p className="muted">Run the bundled calculator project’s JUnit tests. The runner uses a fixed sample and never executes your submitted code.</p>
          <div className="sample-details"><span><b>2</b> JUnit tests</span><span><b>120s</b> time limit</span><span><b>mvnw</b> approved command</span></div>
          <button className="button secondary full-width" onClick={onVerify} disabled={busy}>{busy ? <><span className="button-spinner dark" /> Running tests…</> : 'Run bundled sample tests'} <span>→</span></button>
          {verification && <div className={`verification-result ${verification.status.toLowerCase()}`}><div className="verification-result-title"><strong>{verification.status === 'PASSED' ? 'Tests passed' : verification.status === 'FAILED' ? 'Tests failed' : 'Result inconclusive'}</strong><span>{verification.passedTests} passed · {verification.failedTests} failed</span></div><p>{verification.message}</p>{verification.output && <details><summary>View test output</summary><pre>{verification.output}</pre></details>}</div>}
          <div className="disclaimer"><span>ⓘ</span><p>Sample results do not verify or prove that an AI-suggested change fixes your submitted error.</p></div>
        </div>
      </section>
      <section className="tip-strip"><span className="tip-icon">✦</span><div><strong>Keep the first application frame in view</strong><p>Framework lines can be noisy. The earliest line from your own package is often the best place to start.</p></div><span className="tip-number">DEBUGGING TIP 01</span></section>
    </>
  )
}

function NewAnalysis({ onSubmit, busy }: {
  onSubmit: (data: { title: string; description: string; stackTrace: string; sourceCode: string }) => Promise<void>
  busy: boolean
}) {
  const [title, setTitle] = useState('')
  const [description, setDescription] = useState('')
  const [stackTrace, setStackTrace] = useState('')
  const [sourceCode, setSourceCode] = useState('')
  const [activeTab, setActiveTab] = useState<'trace' | 'code'>('trace')
  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    void onSubmit({ title, description, stackTrace, sourceCode })
  }
  return (
    <section className="form-page">
      <div className="page-heading"><span className="eyebrow">DEBUGGING SESSION</span><h1>What went wrong?</h1><p className="muted">Add the error and enough context to make the diagnosis useful.</p></div>
      <form className="panel analysis-form" onSubmit={submit}>
        <div className="form-step"><span className="step-index">01</span><div><h2>Describe the problem</h2><p>Give this session a name and add what you observed.</p></div></div>
        <label className="field-label">Session name <span className="optional">Optional</span><input value={title} onChange={(event) => setTitle(event.target.value)} placeholder="e.g. Order service returns 500" maxLength={120} /></label>
        <label className="field-label">What were you doing? <span className="optional">Optional</span><textarea value={description} onChange={(event) => setDescription(event.target.value)} placeholder="Describe the request or action that caused the error…" rows={3} maxLength={2000} /></label>
        <div className="form-divider" />
        <div className="form-step"><span className="step-index">02</span><div><h2>Add your error details</h2><p>Paste the complete stack trace. Source code is optional but can help.</p></div></div>
        <div className="editor-tabs"><button type="button" className={activeTab === 'trace' ? 'active' : ''} onClick={() => setActiveTab('trace')}>Stack trace <span className="required-dot">*</span></button><button type="button" className={activeTab === 'code' ? 'active' : ''} onClick={() => setActiveTab('code')}>Source code <span className="optional">Optional</span></button><span className="editor-hint">Java · Spring Boot</span></div>
        {activeTab === 'trace' ? <div className="code-editor"><div className="editor-toolbar"><span className="window-dots"><i /><i /><i /></span><span>error.log</span><span className="editor-count">{stackTrace.length.toLocaleString()} / 20,000</span></div><textarea className="mono-input" value={stackTrace} onChange={(event) => setStackTrace(event.target.value)} placeholder={'java.lang.NullPointerException: Cannot invoke "Order.getId()" because "order" is null\n\tat com.example.orders.OrderService.save(OrderService.java:42)\n\tat ...'} maxLength={20000} required rows={12} /></div> : <div className="code-editor"><div className="editor-toolbar"><span className="window-dots"><i /><i /><i /></span><span>Relevant Java code</span><span className="editor-count">{sourceCode.length.toLocaleString()} / 30,000</span></div><textarea className="mono-input" value={sourceCode} onChange={(event) => setSourceCode(event.target.value)} placeholder={'Paste the method or class related to the error…\n\n// Keep unrelated code out of your submission.'} maxLength={30000} rows={12} /></div>}
        <div className="privacy-note"><span>▣</span><p>Only send code you’re allowed to share. Submitted data is saved in your local database for your analysis history.</p></div>
        <div className="form-actions"><span className="muted"><span className="required-dot">*</span> Required field</span><button type="submit" className="button primary" disabled={busy || !stackTrace.trim()}>{busy ? <><span className="button-spinner" /> Analyzing…</> : <>Analyze error <span>→</span></>}</button></div>
      </form>
      <p className="form-footnote">The result will be labelled <strong>AI-generated</strong> or <strong>Demo</strong>. Neither label means the suggested fix has been verified.</p>
    </section>
  )
}

function AnalysisDetail({ analysis, onBack }: { analysis: Analysis; onBack: () => void }) {
  const isAi = analysis.analysisSource === 'BHARATCODE'
  return (
    <section className="detail-page">
      <button className="back-link" onClick={onBack}>← Back to analyses</button>
      <div className="detail-title-row"><div><span className="eyebrow">ANALYSIS RESULT · {formatDate(analysis.createdAt)}</span><h1>{analysis.title}</h1><p className="muted">{analysis.description || 'No additional description provided.'}</p></div><span className={`source-badge ${isAi ? 'ai' : 'demo'}`}><i />{isAi ? 'BharatCode AI' : 'Local demo'}</span></div>
      {!isAi && <div className="notice demo-notice"><span>◇</span><p>This is a rule-based demo result, not an AI diagnosis. Configure <code>BHARATCODE_API_KEY</code> on the backend to enable provider analysis.</p></div>}
      <div className="result-grid">
        <div className="result-main">
          <article className="panel result-card root-cause-card"><div className="result-card-heading"><span className="result-icon coral">!</span><div><span className="eyebrow">PROBABLE ROOT CAUSE</span><span className="category-pill">{analysis.errorCategory}</span></div></div><h2>{analysis.rootCause}</h2><p>{analysis.explanation}</p></article>
          <article className="panel result-card"><div className="result-card-heading"><span className="result-icon blue">⌕</span><div><span className="eyebrow">SUPPORTING EVIDENCE</span><h2>What points to this?</h2></div></div><BulletList items={analysis.evidence} empty="No evidence details were returned." /></article>
          <article className="panel result-card"><div className="result-card-heading"><span className="result-icon mint">✓</span><div><span className="eyebrow">DEBUGGING PLAN</span><h2>Steps to investigate</h2></div></div><NumberList items={analysis.debuggingSteps} empty="No debugging steps were returned." /></article>
          <article className="panel result-card patch-card"><div className="result-card-heading"><span className="result-icon lilac">⌘</span><div><span className="eyebrow">SUGGESTED CODE FIX</span><h2>Review before applying</h2></div><span className="review-label">MANUAL REVIEW</span></div><p className="muted">This is a suggestion. SpringSentry does not modify your source files.</p><pre className="patch-code">{analysis.suggestedFix}</pre></article>
          <article className="panel result-card"><div className="result-card-heading"><span className="result-icon mint">⊙</span><div><span className="eyebrow">TEST RECOMMENDATIONS</span><h2>What to test next</h2></div></div><BulletList items={analysis.testRecommendations} empty="No tests were recommended." /></article>
        </div>
        <aside className="result-side">
          <div className="panel verification-card"><span className="eyebrow">FIX VERIFICATION</span><div className="verify-state"><span className="verify-not-run">◷</span><div><strong>Not executed</strong><small>No tests have run against this suggestion.</small></div></div><div className="verify-divider" /><p>Generated analysis is not proof that the error is fixed. Run tests against the specific code change to verify it.</p><div className="verification-status-label"><span className="status-dot gray" /> NOT VERIFIED</div></div>
          <div className="panel trace-card"><div className="trace-heading"><span className="eyebrow">SUBMITTED STACK TRACE</span><span>{analysis.stackTrace.length.toLocaleString()} chars</span></div><pre>{analysis.stackTrace}</pre>{analysis.sourceCode && <details><summary>View submitted source code</summary><pre>{analysis.sourceCode}</pre></details>}</div>
        </aside>
      </div>
    </section>
  )
}

function History({ sessions, onOpen, onNew }: { sessions: Analysis[]; onOpen: (id: string) => void; onNew: () => void }) {
  const [query, setQuery] = useState('')
  const filtered = sessions.filter((session) => `${session.title} ${session.errorCategory} ${session.rootCause}`.toLowerCase().includes(query.toLowerCase()))
  return (
    <section className="history-page"><div className="page-heading history-heading"><div><span className="eyebrow">SESSION ARCHIVE</span><h1>Analysis history</h1><p className="muted">Your recent debugging sessions and their results.</p></div><button className="button primary" onClick={onNew}><span>＋</span> New analysis</button></div>
      <div className="panel history-panel"><div className="history-toolbar"><div className="search-wrap"><span>⌕</span><input value={query} onChange={(event) => setQuery(event.target.value)} placeholder="Search analyses…" /></div><span className="history-count">{filtered.length} {filtered.length === 1 ? 'session' : 'sessions'}</span></div>
        {filtered.length ? <div className="table-scroll"><table><thead><tr><th>SESSION</th><th>DIAGNOSIS SOURCE</th><th>CREATED</th><th>VERIFICATION</th><th /></tr></thead><tbody>{filtered.map((session) => <tr key={session.id} onClick={() => onOpen(session.id)}><td><strong>{session.title}</strong><small>{session.errorCategory}</small></td><td><span className={`source-badge compact ${session.analysisSource === 'BHARATCODE' ? 'ai' : 'demo'}`}><i />{session.analysisSource === 'BHARATCODE' ? 'AI' : 'Demo'}</span></td><td>{formatDate(session.createdAt)}</td><td><span className="table-unverified">◷ Not executed</span></td><td><button className="row-arrow" aria-label={`Open ${session.title}`}>→</button></td></tr>)}</tbody></table></div> : <EmptyState title={query ? 'No matching analyses' : 'Your history is empty'} text={query ? 'Try a different search term.' : 'New debugging sessions will appear here.'} action={query ? undefined : 'Start an analysis'} onAction={onNew} />}
      </div>
    </section>
  )
}

function Profile({ user, stats, onSignOut }: { user: User; stats: { total: number; ai: number; demo: number }; onSignOut: () => void }) {
  return <section className="profile-page"><div className="page-heading"><span className="eyebrow">ACCOUNT</span><h1>Your profile</h1><p className="muted">Account details and local workspace information.</p></div><div className="profile-grid"><div className="panel profile-card"><div className="large-avatar">{initials(user.name)}</div><h2>{user.name}</h2><p className="muted">{user.email}</p><span className="account-chip">PERSONAL ACCOUNT</span><div className="profile-stat"><span>Saved analyses</span><strong>{stats.total}</strong></div><div className="profile-stat"><span>AI analyses</span><strong>{stats.ai}</strong></div><div className="profile-stat"><span>Demo analyses</span><strong>{stats.demo}</strong></div></div><div className="panel profile-info"><span className="eyebrow">PRIVACY & CONFIGURATION</span><h2>How this workspace works</h2><ul><li>Passwords are stored as BCrypt hashes in the backend database.</li><li>Your login token is kept in this browser tab’s session storage.</li><li>BharatCode requests are sent only by the backend; the API key is never sent to the browser.</li><li>Without a provider key, analyses are labelled as local demo results.</li><li>Stack traces and source snippets are saved in the local database for your history.</li></ul><div className="profile-info-divider" /><button className="button secondary" onClick={onSignOut}>Sign out <span>↪</span></button></div></div></section>
}

function Metric({ label, value, caption, icon, tone }: { label: string; value: string | number; caption: string; icon: string; tone: string }) {
  return <article className="panel metric-card"><span className={`metric-icon ${tone}`}>{icon}</span><div><span className="metric-label">{label}</span><strong className="metric-value">{value}</strong><small>{caption}</small></div><span className="metric-spark">· · ·</span></article>
}

function SessionRow({ session, onClick }: { session: Analysis; onClick: () => void }) {
  return <button className="session-row" onClick={onClick}><span className="session-mark">{session.errorCategory.includes('Null') ? '!' : '⌕'}</span><span className="session-main"><strong>{session.title}</strong><small>{session.rootCause}</small></span><span className={`source-badge compact ${session.analysisSource === 'BHARATCODE' ? 'ai' : 'demo'}`}><i />{session.analysisSource === 'BHARATCODE' ? 'AI' : 'Demo'}</span><span className="session-date">{formatDate(session.createdAt)}</span><span className="row-arrow">→</span></button>
}

function NavButton({ active, icon, label, onClick }: { active: boolean; icon: string; label: string; onClick: () => void }) {
  return <button className={`nav-button ${active ? 'active' : ''}`} onClick={onClick}><span className="nav-icon">{icon}</span>{label}{active && <span className="nav-active-marker" />}</button>
}

function ProviderStatusBadge({ status, checking, onRefresh }: {
  status: ProviderStatus | null; checking: boolean; onRefresh: () => void
}) {
  const mode = checking || !status ? 'checking' : status.mode.toLowerCase().replace('_', '-')
  const label = checking || !status ? 'Checking AI…'
    : status.mode === 'AI_CONNECTED' ? 'AI Connected'
      : status.mode === 'DEMO_MODE' ? 'Demo Mode' : 'AI Unavailable'
  const title = checking ? 'Checking BharatCode connectivity…' : status?.message || 'Provider status is not available.'
  return <span className={`provider-status ${mode}`} title={title}>
    <span className="status-dot" />
    <span className="provider-status-label">{label}</span>
    <button type="button" onClick={onRefresh} disabled={checking} title="Recheck BharatCode status" aria-label="Recheck BharatCode status">↻</button>
  </span>
}

function EmptyState({ title, text, action, onAction }: { title: string; text: string; action?: string; onAction?: () => void }) {
  return <div className="empty-state"><span className="empty-icon">⌕</span><strong>{title}</strong><p>{text}</p>{action && onAction && <button className="text-button" onClick={onAction}>{action} <span>→</span></button>}</div>
}

function BulletList({ items, empty }: { items: string[]; empty: string }) {
  if (!items.length) return <p className="muted">{empty}</p>
  return <ul className="bullet-list">{items.map((item, index) => <li key={`${index}-${item}`}>{item}</li>)}</ul>
}

function NumberList({ items, empty }: { items: string[]; empty: string }) {
  if (!items.length) return <p className="muted">{empty}</p>
  return <ol className="number-list">{items.map((item, index) => <li key={`${index}-${item}`}><span>{String(index + 1).padStart(2, '0')}</span>{item}</li>)}</ol>
}

function pageTitle(page: Page) {
  return ({ dashboard: 'Overview', new: 'New analysis', history: 'Analysis history', profile: 'Your profile', result: 'Analysis result' })[page]
}

function initials(name: string) {
  return name.trim().split(/\s+/).slice(0, 2).map((part) => part[0]?.toUpperCase() || '').join('')
}

function formatDate(value: string) {
  return new Intl.DateTimeFormat(undefined, { month: 'short', day: 'numeric', year: 'numeric' }).format(new Date(value))
}
