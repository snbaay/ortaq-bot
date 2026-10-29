# Deploy Ortaq on Neon and Render

Checked against provider documentation on 29 October 2026. Accounts and bot credentials must be created by the owner.

## 1. Create the PostgreSQL database

1. Sign in to [Neon](https://console.neon.tech/) and create a Free project in a region near Render.
2. Open the project dashboard and copy the host, database name, username, and password from **Connect**. Keep the password private.
3. Build the JDBC URL from those values: `jdbc:postgresql://HOST/DB?sslmode=require`. Put the username and password in separate environment variables. Neon requires TLS; see its [connection guidance](https://neon.com/blog/oops-proof-your-vibe-code-with-neon-because-mistakes-happen).

As of October 2026, [Neon says](https://neon.com/blog/neon-free-plan-1-gb-per-project) its Free plan has 100 projects, 1 GB storage per project, and 100 CU-hours of compute per project per month. Check the current plan before relying on those limits.

## 2. Create the Render service

1. In the [Render dashboard](https://dashboard.render.com/), choose **New → Web Service** and connect the GitHub repo `snbaay/ortaq-bot`.
2. Select the `dev` branch, **Docker** as the language, and the Free instance type. The repository root already has a `Dockerfile`; Render will build it. See [Render's Docker setup](https://render.com/docs/docker).
3. Set these environment variables in Render, using your own values:

   | Name | Value |
   | --- | --- |
   | `BOT_TOKEN` | Production bot token from BotFather |
   | `BOT_USERNAME` | Production bot username, without `@` |
   | `SPRING_DATASOURCE_URL` | `jdbc:postgresql://HOST/DB?sslmode=require` |
   | `SPRING_DATASOURCE_USERNAME` | Neon database username |
   | `SPRING_DATASOURCE_PASSWORD` | Neon database password |
   | `PORT` | `8080` or Render's provided port |
   | `ALLOWED_CHAT_IDS` | Optional comma-separated Telegram group IDs |

4. Deploy. Open `https://<service>.onrender.com/health` and expect `OK`. Check the Render logs for successful startup, then add the bot to a Telegram group and try `/start`, `/komek`, and `/shygyn 630 су`.

Disable privacy mode for the production bot in BotFather (`/setprivacy`) if you want plain messages such as `630 су` to be recorded. Each roommate must send a message before they are included in a split.

## 3. Keep the free service awake

[Render Free web services](https://render.com/docs/free) spin down after 15 minutes without **incoming** HTTP or WebSocket traffic. Telegram long polling makes outgoing requests, so it does not prevent spin-down. A sleeping bot cannot process Telegram updates or run its morning digest until woken.

Create a free HTTP monitor in [UptimeRobot](https://uptimerobot.com/) or a job in [cron-job.org](https://cron-job.org/EN/) that requests `https://<service>.onrender.com/health` every 5 minutes. [UptimeRobot's Free interval](https://help.uptimerobot.com/en/articles/11360876-what-is-a-monitoring-interval-in-uptimerobot) is 5 minutes; cron-job.org supports intervals as short as 1 minute. Verify that the monitor reports HTTP 200 and that Render stays active.

Render currently offers 750 Free instance hours per workspace per month and may restart or suspend Free services under its [usage rules](https://render.com/docs/free). A continuously awake service will consume those hours. The container filesystem is ephemeral; the bot stores durable data in Neon.

## 4. Use a different bot locally

Create a **second** bot in BotFather for local development and use its token in your own terminal. Never run local and deployed instances with the same token: two long polling clients for one bot can trigger Telegram error 409. Keep credentials out of Git and never paste tokens into issues or logs.
