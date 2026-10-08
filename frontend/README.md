# Warden frontend

Warden is a permission-aware campus assistant frontend for Ashbrook Institute of Technology. It uses the in-memory mock API by default; the backend contract is under `/api`.

## Requirements

- Node 20 or later
- npm

## Run the mock

```sh
npm install
cp .env.example .env
npm run dev
```

The default is `VITE_USE_MOCK=true`. Demo password for every account: `Warden@2026`.

- Students: Aarav Sharma, Isha Verma, Kabir Singh, Ananya Gupta
- Faculty: Dr. Meera Iyer, Prof. Arjun Bhatia, Prof. Rohan Kapoor
- Heads of department: Dr. Sunita Rao, Dr. Rakesh Menon
- Administrators: Priya Nair, Vikram Sethi

## Use the real API

Set `VITE_USE_MOCK=false` in the environment. Vite proxies `/api` to `http://localhost:8080`; the frontend uses same-origin requests and the backend refresh cookie. No source changes are needed.

## Scripts

- `npm run dev` — Vite development server
- `npm run build` — strict TypeScript build and production bundle
- `npm run preview` — serve the production bundle locally
- `npm run lint` — ESLint
- `npm run lint:style` — prompt-supplied visual style linter
- `npm run typecheck` — TypeScript check
