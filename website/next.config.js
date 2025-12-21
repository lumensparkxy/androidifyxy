/** @type {import('next').NextConfig} */
const nextConfig = {
  output: 'export',
  images: {
    unoptimized: true,
  },
  trailingSlash: true,
  // GitHub Pages deployment - repo name as basePath
  basePath: '/androidifyxy',
  assetPrefix: '/androidifyxy/',
};

module.exports = nextConfig;
