import type { Metadata } from 'next';
import './globals.css';
import { Header, Footer, WhatsAppFloatingButton } from '@/components';

export const metadata: Metadata = {
  title: 'Krishi AI - AI Farming Assistant for Indian Farmers',
  description: 'Krishi AI (कृषि AI) is your AI-powered farming companion. Get plant diagnosis (crop disease & pest insights), AI chat in your local language, live Mandi prices, and more.',
  keywords: ['Krishi AI', 'farming app', 'agriculture', 'AI farming assistant', 'plant diagnosis', 'crop disease', 'pest identification', 'Mandi prices', 'Indian farmers', 'कृषि AI'],
  authors: [{ name: 'Maswadkar Developers' }],
  openGraph: {
    title: 'Krishi AI - AI Farming Assistant',
    description: 'Plant diagnosis + AI chat for Indian farmers (in your local language)',
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
