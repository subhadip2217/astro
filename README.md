# ✨ Cosmic Numerology

A beautiful, fully client-side **Numerology + Rashi** web app.  
Enter your date of birth and instantly discover your cosmic blueprint — no servers, no data collection.

![License](https://img.shields.io/badge/license-MIT-purple)
![Pure HTML](https://img.shields.io/badge/stack-HTML%20%2B%20CSS%20%2B%20JS-blue)
![Privacy](https://img.shields.io/badge/privacy-100%25%20local-green)

## 🔮 Features

- **Life Path Number** (with Master Numbers 11 & 22 supported)
- **Birth Number** (day of birth reduced)
- **Rashi / Zodiac** (approximate sidereal ranges + symbol)
- Personality description
- Strengths & growth areas
- Lucky numbers & lucky colours (with colour swatches)
- **Today’s Cosmic Vibe** (personal day calculation)
- Friendship / relationship compatibility insights
- Personalized result card
- **Download result as image** (html2canvas)
- Share via native share or clipboard
- “Calculate Again” button
- Fully responsive (mobile-first)
- Dark, glowing modern UI with animated starfield
- **100% private** — everything calculated in the browser. Your DOB never leaves your device.

## 🚀 Live Demo

After pushing to GitHub, enable **GitHub Pages**:

1. Go to your repository → **Settings** → **Pages**
2. Source: Deploy from branch → `main` / root
3. Your site will be live at:  
   `https://YOUR_USERNAME.github.io/REPO_NAME/`

## 📂 Project Structure

```
.
├── index.html          # Complete single-file app (HTML + CSS + JS)
└── README.md
```

Just one file! Easy to host anywhere (GitHub Pages, Netlify, Vercel, or even open locally).

## 🧮 How Calculations Work

### Life Path Number
Month, Day and Year are reduced separately (preserving Master Numbers 11 & 22), then summed and reduced again.

### Birth Number
Simply the day of the month reduced to a single digit (1–9).

### Rashi
Uses approximate sidereal (Vedic-style) date ranges.  
**Note:** True *Janma Rashi* (Moon sign) requires exact birth time and place. This version provides a useful educational approximation based on birth date.

### Today’s Vibe
Combines the current calendar day with your Life Path to generate a personal day number and matching message.

## 🛠️ Local Development

```bash
# Simply open the file
open index.html
# or
python -m http.server 8000
```

No build step required.

## 📸 Screenshots

(Add your own screenshots after deploying!)

## 🤝 Contributing

Feel free to open issues or PRs for:

- Better Rashi accuracy (or optional birth-time Moon sign)
- More languages
- Additional numerology numbers (Destiny, Soul Urge, etc.)
- Accessibility improvements

## 📄 License

MIT — free to use, modify and share.

---

Made with curiosity and cosmic energy 🌌  
**Your numbers. Your privacy. Your journey.**
