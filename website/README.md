# Krishi AI Website

Modern, minimalist informational website for Krishi AI (कृषि AI) - an AI-powered farming assistant for Indian farmers.

## Tech Stack

- **Framework:** Next.js 15 (App Router)
- **Language:** TypeScript
- **Styling:** Tailwind CSS
- **Icons:** Lucide React
- **Hosting:** Firebase Hosting

## Getting Started

### Prerequisites

- Node.js 18+ 
- npm or yarn

### Installation

```bash
cd website
npm install
```

### Development

```bash
npm run dev
```

Open [http://localhost:3000](http://localhost:3000) in your browser.

### Build

```bash
npm run build
```

This creates a static export in the `out/` directory.

### Deploy to Firebase

```bash
npm run deploy
```

Or deploy manually:

```bash
npm run build
cd ../functions
firebase deploy --only hosting
```

## Project Structure

```
website/
├── app/                    # Next.js App Router pages
│   ├── layout.tsx         # Root layout with Header/Footer
│   ├── page.tsx           # Home page
│   ├── services/          # Services page
│   ├── about/             # About page
│   ├── contact/           # Contact page
│   ├── privacy/           # Privacy Policy
│   ├── terms/             # Terms of Service
│   └── not-found.tsx      # 404 page
├── components/            # Reusable UI components
│   ├── Header.tsx
│   ├── Footer.tsx
│   ├── Button.tsx
│   ├── Card.tsx
│   └── ...
├── public/                # Static assets
└── tailwind.config.ts     # Tailwind theme configuration
```

## Color Theme

Based on the Krishi AI Android app's agriculture theme:

| Color | Hex | Usage |
|-------|-----|-------|
| Primary | `#2E7D32` | Main green |
| Secondary | `#558B2F` | Lime green |
| Tertiary | `#00796B` | Teal accents |
| Background | `#FDFDF5` | Off-white |

## Pages

- **Home** - Hero, features grid, benefits, stats, CTA
- **Services** - Detailed service descriptions
- **About** - Mission, vision, values, team info
- **Contact** - Email & WhatsApp links, FAQ
- **Privacy Policy** - Standard privacy policy
- **Terms of Service** - Usage terms and disclaimers

## Contact

- **Email:** support@krishiai.pro
- **WhatsApp:** +91 94035 13382
- **Play Store:** [Download Krishi AI](https://play.google.com/store/apps/details?id=com.maswadkar.androidxy)

## License

© 2025 Maswadkar Developers. All rights reserved.
