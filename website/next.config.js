/** @type {import('next').NextConfig} */
const nextConfig = {
  output: 'export',
  images: {
    unoptimized: true,
  },
  trailingSlash: true,
  // Uncomment and set basePath if deploying to a subpath (e.g., /repo-name)
  // basePath: '/your-repo-name',
  // assetPrefix: '/your-repo-name/',
};

module.exports = nextConfig;
