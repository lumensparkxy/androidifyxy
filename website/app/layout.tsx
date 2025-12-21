import type { Metadata } from 'next';
import './globals.css';
import { Header, Footer, WhatsAppFloatingButton } from '@/components';

export const metadata: Metadata = {
  title: 'Krishi AI - AI Farming Assistant for Indian Farmers',
  description: 'Krishi AI (कृषि AI) is your AI-powered farming companion. Get expert agricultural advice, live Mandi prices, crop disease diagnosis, and more in Hindi, Marathi, and English.',
  keywords: ['Krishi AI', 'farming app', 'agriculture', 'AI farming assistant', 'Mandi prices', 'crop disease', 'Indian farmers', 'कृषि AI'],
  authors: [{ name: 'Maswadkar Developers' }],
  openGraph: {
    title: 'Krishi AI - AI Farming Assistant',
    description: 'Your AI-powered farming companion for Indian farmers',
    type: 'website',
    locale: 'en_IN',
  },
};

export default function RootLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  return (
    <html lang="en">
      <body className="min-h-screen flex flex-col">
        <Header />
        <main className="flex-grow pt-16 md:pt-20">
          {children}
        </main>
        <Footer />
        <WhatsAppFloatingButton phoneNumber="919403513382" />
      </body>
    </html>
  );
}
