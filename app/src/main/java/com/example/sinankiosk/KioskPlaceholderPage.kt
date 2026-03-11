package com.example.sinankiosk

internal fun buildKioskPlaceholderHtml(uiState: MainUiState): String {
    val title = if (uiState.isPinConfigured) {
        "Kiosk not configured"
    } else {
        "Kiosk setup required"
    }
    val subtitle = if (uiState.isPinConfigured) {
        "No website has been assigned to this device yet."
    } else {
        "This device is waiting for its first kiosk configuration."
    }
    val adminStatus = if (uiState.isPinConfigured) {
        "Administrator access is ready"
    } else {
        "Administrator setup is still incomplete"
    }
    val overviewBody = if (uiState.isPinConfigured) {
        "This phone is reserved for a single managed web experience. Once an administrator assigns a website, it will open here and continue across its internal routes inside the kiosk."
    } else {
        "This phone has not completed its first-time kiosk setup yet. An administrator still needs to finish the initial configuration before the assigned website can launch."
    }
    val nextStepBody = if (uiState.isPinConfigured) {
        "An administrator only needs to assign the website entry URL. After that, this device will launch directly into the assigned web workspace."
    } else {
        "The administrator must complete setup, create the kiosk configuration, and assign the website that should run on this phone."
    }
    val readinessLabel = if (uiState.isPinConfigured) "Ready for website assignment" else "Waiting for first-time setup"
    val readinessTone = if (uiState.isPinConfigured) "ok" else "warn"
    val adminIcon = materialIconSvg("admin_panel_settings")
    val languageIcon = materialIconSvg("language")
    val devicesIcon = materialIconSvg("devices")
    val checklistIcon = materialIconSvg("checklist")
    val hourglassIcon = materialIconSvg("hourglass_top")

    return """
        <!DOCTYPE html>
        <html lang="en">
        <head>
          <meta charset="utf-8" />
          <meta name="viewport" content="width=device-width, initial-scale=1, viewport-fit=cover" />
          <title>Kiosk Standby</title>
          <style>
            :root {
              color-scheme: dark;
              --bg-1: #071018;
              --bg-2: #0d1721;
              --panel: rgba(17, 25, 34, 0.92);
              --panel-strong: rgba(20, 31, 42, 0.96);
              --border: rgba(132, 160, 181, 0.16);
              --text: #f2f6fa;
              --muted: #9cb0bf;
              --accent: #1ea7a1;
              --accent-soft: rgba(30, 167, 161, 0.14);
              --success: #33b078;
              --warning: #f0b24e;
              --shadow: 0 22px 60px rgba(0, 0, 0, 0.28);
            }

            * {
              box-sizing: border-box;
            }

            html {
              min-height: 100%;
              background:
                radial-gradient(circle at top left, rgba(30, 167, 161, 0.18), transparent 30%),
                radial-gradient(circle at top right, rgba(52, 120, 194, 0.18), transparent 28%),
                linear-gradient(180deg, var(--bg-2) 0%, var(--bg-1) 100%);
            }

            body {
              margin: 0;
              min-height: 100vh;
              font-family: "Segoe UI", "Trebuchet MS", sans-serif;
              color: var(--text);
              background: transparent;
            }

            .page {
              width: min(100%, 820px);
              margin: 0 auto;
              padding: 24px 18px 48px;
            }

            .hero {
              position: relative;
              overflow: hidden;
              padding: 24px;
              border-radius: 28px;
              background:
                linear-gradient(140deg, rgba(30, 167, 161, 0.18), rgba(10, 18, 26, 0.95) 48%),
                var(--panel-strong);
              border: 1px solid var(--border);
              box-shadow: var(--shadow);
            }

            .hero::after {
              content: "";
              position: absolute;
              inset: auto -60px -90px auto;
              width: 220px;
              height: 220px;
              border-radius: 50%;
              background: radial-gradient(circle, rgba(30, 167, 161, 0.25), transparent 70%);
              pointer-events: none;
            }

            .eyebrow {
              display: inline-flex;
              align-items: center;
              gap: 8px;
              padding: 8px 12px;
              border-radius: 999px;
              background: var(--accent-soft);
              color: #c5f5f0;
              font-size: 0.78rem;
              font-weight: 700;
              letter-spacing: 0.08em;
              text-transform: uppercase;
            }

            .icon {
              width: 20px;
              height: 20px;
              display: inline-flex;
              align-items: center;
              justify-content: center;
              flex: 0 0 20px;
            }

            .icon svg {
              width: 20px;
              height: 20px;
              display: block;
              fill: currentColor;
            }

            h1 {
              margin: 16px 0 10px;
              font-size: clamp(1.9rem, 4vw, 2.7rem);
              line-height: 1.05;
              letter-spacing: -0.03em;
            }

            .lead {
              margin: 0;
              color: var(--muted);
              font-size: 1rem;
              line-height: 1.7;
              max-width: 44rem;
            }

            .status-row {
              display: flex;
              flex-wrap: wrap;
              gap: 10px;
              margin-top: 18px;
            }

            .pill {
              display: inline-flex;
              align-items: center;
              gap: 8px;
              padding: 10px 14px;
              border-radius: 999px;
              border: 1px solid transparent;
              font-size: 0.92rem;
              color: var(--text);
              background: rgba(255, 255, 255, 0.04);
            }

            .pill.ok {
              border-color: rgba(51, 176, 120, 0.32);
              background: rgba(51, 176, 120, 0.12);
            }

            .pill.warn {
              border-color: rgba(240, 178, 78, 0.35);
              background: rgba(240, 178, 78, 0.12);
            }

            .pill.info {
              border-color: rgba(30, 167, 161, 0.28);
              background: rgba(30, 167, 161, 0.11);
            }

            .section {
              margin-top: 18px;
              padding: 20px;
              border-radius: 24px;
              background: var(--panel);
              border: 1px solid var(--border);
              box-shadow: var(--shadow);
            }

            .section-title {
              margin: 0 0 8px;
              font-size: 1.08rem;
              font-weight: 700;
              letter-spacing: -0.02em;
            }

            .section-heading,
            .card-heading {
              display: flex;
              align-items: center;
              gap: 12px;
              margin-bottom: 8px;
            }

            .section-heading .icon,
            .card-heading .icon {
              width: 38px;
              height: 38px;
              border-radius: 14px;
              background: rgba(30, 167, 161, 0.12);
              color: var(--accent);
            }

            .section-copy {
              margin: 0;
              color: var(--muted);
              line-height: 1.72;
              font-size: 0.98rem;
            }

            .grid {
              display: grid;
              grid-template-columns: repeat(12, minmax(0, 1fr));
              gap: 16px;
              margin-top: 18px;
            }

            .card {
              grid-column: span 12;
              padding: 18px;
              border-radius: 24px;
              background: var(--panel);
              border: 1px solid var(--border);
              box-shadow: var(--shadow);
            }

            .card h2 {
              margin: 0;
              font-size: 1rem;
              letter-spacing: -0.01em;
            }

            .card p {
              margin: 0;
              color: var(--muted);
              line-height: 1.7;
            }

            .metric {
              display: inline-flex;
              align-items: center;
              gap: 8px;
              margin-top: 14px;
              padding: 10px 12px;
              border-radius: 16px;
              background: rgba(255, 255, 255, 0.03);
              color: var(--text);
              font-size: 0.92rem;
            }

            .list {
              margin: 14px 0 0;
              padding: 0;
              list-style: none;
              display: grid;
              gap: 12px;
            }

            .list li {
              display: flex;
              gap: 12px;
              align-items: flex-start;
              color: var(--muted);
              line-height: 1.65;
            }

            .list-bullet {
              flex: 0 0 10px;
              width: 10px;
              height: 10px;
              margin-top: 8px;
              border-radius: 50%;
              background: var(--accent);
              box-shadow: 0 0 0 6px rgba(30, 167, 161, 0.12);
            }

            .notice {
              margin-top: 18px;
              padding: 18px 20px;
              border-radius: 22px;
              border: 1px dashed rgba(30, 167, 161, 0.35);
              background: rgba(30, 167, 161, 0.08);
            }

            .notice strong {
              display: block;
              margin-bottom: 6px;
              font-size: 0.98rem;
            }

            .footer {
              margin-top: 20px;
              color: rgba(156, 176, 191, 0.78);
              text-align: center;
              font-size: 0.86rem;
              line-height: 1.6;
            }

            @media (min-width: 720px) {
              .page {
                padding: 32px 24px 56px;
              }

              .card.span-6 {
                grid-column: span 6;
              }
            }
          </style>
        </head>
        <body>
          <main class="page">
            <section class="hero">
              <div class="eyebrow"><span class="icon">$devicesIcon</span>Kiosk standby</div>
              <h1>${escapePlaceholderHtml(title)}</h1>
              <p class="lead">${escapePlaceholderHtml(subtitle)}</p>
              <div class="status-row">
                <div class="pill $readinessTone">${escapePlaceholderHtml(readinessLabel)}</div>
                <div class="pill ${if (uiState.isPinConfigured) "ok" else "warn"}">${escapePlaceholderHtml(adminStatus)}</div>
                <div class="pill info">Website entry URL: none configured</div>
              </div>
            </section>

            <section class="section">
              <div class="section-heading">
                <span class="icon">$infoIcon</span>
                <h2 class="section-title">Overview</h2>
              </div>
              <p class="section-copy">${escapePlaceholderHtml(overviewBody)}</p>
            </section>

            <section class="grid">
              <article class="card span-6">
                <div class="card-heading">
                  <span class="icon">$languageIcon</span>
                  <h2>Current assignment</h2>
                </div>
                <p>No website is currently attached to this kiosk. Until one is assigned, this device stays in a safe standby state.</p>
                <div class="metric">Assigned URL: none</div>
              </article>
              <article class="card span-6">
                <div class="card-heading">
                  <span class="icon">$adminIcon</span>
                  <h2>Next step</h2>
                </div>
                <p>${escapePlaceholderHtml(nextStepBody)}</p>
                <div class="metric">Configuration source: administrator only</div>
              </article>
              <article class="card span-6">
                <div class="card-heading">
                  <span class="icon">$devicesIcon</span>
                  <h2>Device behavior</h2>
                </div>
                <p>This phone is prepared for a single-purpose experience. Once configured, the assigned website opens here and continues inside the kiosk flow.</p>
              </article>
              <article class="card span-6">
                <div class="card-heading">
                  <span class="icon">$checklistIcon</span>
                  <h2>Status snapshot</h2>
                </div>
                <ul class="list">
                  <li><span class="list-bullet"></span><span>Website assignment: pending</span></li>
                  <li><span class="list-bullet"></span><span>Administrator readiness: ${escapePlaceholderHtml(adminStatus)}</span></li>
                  <li><span class="list-bullet"></span><span>Kiosk shell: available on this device</span></li>
                </ul>
              </article>
            </section>

            <section class="section">
              <div class="section-heading">
                <span class="icon">$hourglassIcon</span>
                <h2 class="section-title">What users can expect</h2>
              </div>
              <ul class="list">
                <li><span class="list-bullet"></span><span>The assigned website will appear directly in this kiosk once configuration is complete.</span></li>
                <li><span class="list-bullet"></span><span>Internal pages and routes of that website will continue inside the same kiosk view.</span></li>
                <li><span class="list-bullet"></span><span>If this screen is still visible, the device simply has not received its website assignment yet.</span></li>
              </ul>
            </section>

            <section class="notice">
              <strong>Waiting for administrator action</strong>
              <span>This placeholder page is intentionally simple. It confirms that the kiosk app is active and the device is ready, while waiting for the assigned website to be configured.</span>
            </section>

            <div class="footer">
              Managed kiosk device<br />
              This screen will be replaced automatically after a website is assigned.
            </div>
          </main>
        </body>
        </html>
    """.trimIndent()
}

private fun escapePlaceholderHtml(value: String): String =
    buildString(value.length) {
        value.forEach { character ->
            when (character) {
                '&' -> append("&amp;")
                '<' -> append("&lt;")
                '>' -> append("&gt;")
                '"' -> append("&quot;")
                '\'' -> append("&#39;")
                else -> append(character)
            }
        }
    }

private val infoIcon = materialIconSvg("info")

private fun materialIconSvg(name: String): String {
    val path = when (name) {
        "admin_panel_settings" -> "M12 1 3 5v6c0 5.55 3.84 10.74 9 12 5.16-1.26 9-6.45 9-12V5l-9-4zm0 2.18 7 3.11V11c0 4.52-2.98 8.69-7 9.93C7.98 19.69 5 15.52 5 11V6.29l7-3.11zm0 2.82a3 3 0 0 0-3 3c0 1.31.84 2.42 2 2.83V15h2v-3.17A2.99 2.99 0 0 0 15 9a3 3 0 0 0-3-3z"
        "language" -> "M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm6.93 9h-3.11a15.73 15.73 0 0 0-1.38-5.01A8.03 8.03 0 0 1 18.93 11zM12 4c1.73 2.08 2.84 4.64 3.23 7H8.77C9.16 8.64 10.27 6.08 12 4zM4.07 13h3.11c.24 1.8.77 3.5 1.55 5.01A8.03 8.03 0 0 1 4.07 13zm3.11-2H4.07a8.03 8.03 0 0 1 4.66-5.01A15.73 15.73 0 0 0 7.18 11zM12 20c-1.73-2.08-2.84-4.64-3.23-7h6.46C14.84 15.36 13.73 17.92 12 20zm2.27-1.99c.78-1.51 1.31-3.21 1.55-5.01h3.11a8.03 8.03 0 0 1-4.66 5.01z"
        "devices" -> "M4 6h18V4H4c-1.1 0-2 .9-2 2v10h2V6zm4 4h14c1.1 0 2 .9 2 2v8c0 1.1-.9 2-2 2H8c-1.1 0-2-.9-2-2v-8c0-1.1.9-2 2-2zm0 10h14v-8H8v8zm-4-3H0v3c0 1.1.9 2 2 2h3v-2H2v-1h2v-2z"
        "checklist" -> "M19 3H5c-1.1 0-2 .9-2 2v14h2V5h14V3zm-3 4H9c-.55 0-1 .45-1 1v12c0 .55.45 1 1 1h9c.55 0 1-.45 1-1V11l-3-4zm1 12H10V8h5v4h2v7zM6.41 12.01 5 13.42l2 2 4-4-1.41-1.41L7 12.59l-.59-.58z"
        "hourglass_top" -> "M6 2v6h.01L10 12l-3.99 4H6v6h12v-6h-.01L14 12l3.99-4H18V2H6zm10 2v3.17L12 11.17 8 7.17V4h8zm-8 16v-3.17l4-4 4 4V20H8z"
        else -> "M11 7h2v2h-2zm0 4h2v6h-2zm1-9C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2z"
    }

    return """<svg viewBox="0 0 24 24" aria-hidden="true" focusable="false"><path d="$path"></path></svg>"""
}
