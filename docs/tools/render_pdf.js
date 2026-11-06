// Prints an HTML file to PDF with Chromium (Playwright). Usage: node render_pdf.js in.html out.pdf
const { chromium } = require('playwright');
(async () => {
  const b = await chromium.launch();
  const p = await b.newPage();
  await p.goto('file://' + process.argv[2], { waitUntil: 'load' });
  await p.pdf({ path: process.argv[3], preferCSSPageSize: true, printBackground: true });
  await b.close();
})();
