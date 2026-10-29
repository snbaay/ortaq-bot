# Deploy Ortaq on Neon and Render

Checked against provider documentation on 29 October 2026. Accounts and bot credentials must be created by the owner.

## 1. Create the PostgreSQL database

1. Sign in to [Neon](https://console.neon.tech/) and create a Free project in a region near Render.
2. Open the project dashboard and copy the host, database name, username, and password from **Connect**. Keep the password private.
3. Build the JDBC URL from those values: `jdbc:postgresql://HOST/DB?sslmode=require`. Put the username and password in separate environment variables. Neon requires TLS; see its [connection guidance](https://neon.com/blog/oops-proof-your-vibe-code-with-neon-because-mistakes-happen).

As of October 2026, [Neon says](https://neon.com/blog/neon-free-plan-1-gb-per-project) its Free plan has 100 projects, 1 GB storage per project, and 100 CU-hours of compute per project per month. Check the current plan before relying on those limits.

## 2. Create the Render service

1. In the [Render dashboard](https://dashboard.render.com/), choose **New → Blueprint** and connect the GitHub repo `snbaay/ortaq-bot` on the `dev` branch. The root [`render.yaml`](../render.yaml) defines exactly one Free Docker web service, its `dev` branch, and `/health` as the health check. Check whether a service named `ortaq-bot` already exists before creating a second one. See [Render's Blueprint reference](https://render.com/docs/blueprint-spec).
2. During the initial Blueprint setup, Render prompts for the five variables marked `sync: false`. Enter your own values:

   | Name | Value |
   | --- | --- |
   | `BOT_TOKEN` | Production bot token from BotFather |
   | `BOT_USERNAME` | Production bot username, without `@` |
   | `SPRING_DATASOURCE_URL` | `jdbc:postgresql://HOST/DB?sslmode=require` |
   | `SPRING_DATASOURCE_USERNAME` | Neon database username |
   | `SPRING_DATASOURCE_PASSWORD` | Neon database password |
   Render provides `PORT` (default `10000`), which the app reads automatically. Add `ALLOWED_CHAT_IDS` in the service environment later if you want to limit allowed Telegram groups. If a Blueprint already exists, add any newly required secret in the service environment; Render only prompts for `sync: false` variables on initial creation.

3. Deploy. Open `https://<service>.onrender.com/health` and expect `OK`. Check the Render logs for successful startup, then add the bot to a Telegram group and try `/start`, `/komek`, `/shygyn 630 су`, and `/kezek_qoyu дүйсенбі @username` followed by `/kezek`.

Disable privacy mode for the production bot in BotFather (`/setprivacy`) if you want plain messages such as `630 су` to be recorded. Each roommate must send a message before they are included in a split.

## 3. Keep the free service awake

[Render Free web services](https://render.com/docs/free) spin down after 15 minutes without **incoming** HTTP or WebSocket traffic. Telegram long polling makes outgoing requests, so it does not prevent spin-down. A sleeping bot cannot process Telegram updates or run its morning digest until woken.

After the service has a real URL, create one **HTTP(S)** monitor in [UptimeRobot](https://uptimerobot.com/) for `https://<service>.onrender.com/health`. Set the interval to **5 minutes** (10 minutes also stays below Render's 15-minute idle timeout), then verify HTTP 200 in the monitor and a request in the Render logs. UptimeRobot's [Free plan](https://help.uptimerobot.com/en/articles/11604710-who-should-use-uptimerobot-s-free-plan) includes 50 monitors with 5-minute checks. Alternatively, create a [cron-job.org](https://cron-job.org/en/) job with the same URL and a 5- or 10-minute interval; its free service supports requests as often as once a minute. Monitoring checks can fail or be delayed, so this is not an uptime guarantee.

Do not monitor `/robots.txt`: [Render answers that path itself while the service sleeps](https://render.com/docs/free), so it does not wake the bot. The `dev` branch selects the code to deploy; it does not keep Render awake. [Vercel Hobby Cron](https://vercel.com/docs/cron-jobs/usage-and-pricing) runs at most once per day, so it cannot supply the 5- or 10-minute pings. This repository contains a persistent Java Telegram long-polling bot, so the bot runs on Render; there is no separate frontend to deploy on Vercel.

Render currently offers 750 Free instance hours **per workspace** per calendar month and may restart or suspend Free services under its [usage rules](https://render.com/docs/free). One continuously awake service consumes 720 hours in a 30-day month or 744 hours in a 31-day month, leaving 30 or 6 hours respectively; a second continuous Free service would exceed the allowance. Check actual usage in the Render dashboard. The container filesystem is ephemeral; the bot stores durable data in Neon.

## 4. Use a different bot locally

Create a **second** bot in BotFather for local development and use its token in your own terminal. Never run local and deployed instances with the same token: two long polling clients for one bot can trigger Telegram error 409. Keep credentials out of Git and never paste tokens into issues or logs.
