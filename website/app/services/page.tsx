import { Metadata } from 'next';
import Image from 'next/image';
import { Bot, Mic, Camera, TrendingUp, History, Globe, Smartphone, MessageSquare } from 'lucide-react';
import { Container, SectionHeading, Card, CardIcon, CardTitle, CardDescription, Button } from '@/components';

export const metadata: Metadata = {
  title: 'Services - Krishi AI',
  description: 'Explore Krishi AI\'s powerful features: AI Chat Assistant, Voice Conversations, Crop Disease Diagnosis, Live Mandi Prices, and more.',
};

const services = [
  {
    icon: Bot,
    title: 'AI Chat Assistant',
    description: 'Get instant, accurate answers to all your farming questions. Our AI assistant is trained on comprehensive agricultural knowledge and understands the specific needs of Indian farmers.',
    screenshot: '/screenshots/hero-screenshot.png',
    features: [
      'Expert advice on crop cultivation',
      'Soil health and fertilizer recommendations',
      'Pest and disease management',
      'Weather-based farming tips',
      'Organic farming guidance',
    ],
  },
  {
    icon: Mic,
    title: 'Voice Conversations',
    description: 'Speak naturally to Krishi AI in your preferred language. No need to type — just talk and get spoken responses. Perfect for hands-free use while working in the fields.',
    screenshot: '/screenshots/voice-assistant.png',
    features: [
      'Real-time voice recognition',
      'Natural language understanding',
      'Spoken responses in your language',
      'Works in Hindi, Marathi, English & more',
      'Hands-free operation',
    ],
  },
  {
    icon: Camera,
    title: 'Crop Disease Diagnosis',
    description: 'Take a photo of your affected crop and get instant disease identification. Our AI analyzes the image and provides detailed information about the disease and recommended treatments.',
    screenshot: '/screenshots/crop-scan.png',
    features: [
      'Instant disease identification',
      'Detailed disease information',
      'Treatment recommendations',
      'Preventive measures',
      'Works offline (coming soon)',
    ],
  },
  {
    icon: TrendingUp,
    title: 'Live Mandi Prices',
    description: 'Access real-time agricultural commodity prices from mandis across Maharashtra. Make informed decisions about when and where to sell your produce for the best returns.',
    screenshot: '/screenshots/market-prices.png',
    features: [
      'Real-time price updates',
      'Multiple commodities tracked',
      'District and market-wise prices',
      'Price trends and comparisons',
      'Save your preferred markets',
    ],
  },
  {
    icon: History,
    title: 'Chat History',
    description: 'All your conversations with Krishi AI are saved securely. Revisit previous advice, recommendations, and information whenever you need it.',
    screenshot: '/screenshots/history.png',
    features: [
      'Automatic conversation saving',
      'Easy search and retrieval',
      'Cloud sync across devices',
      'Secure and private',
      'Export conversations',
    ],
  },
  {
    icon: Globe,
    title: 'Multilingual Support',
    description: 'Krishi AI speaks your language. Get farming advice in Hindi, Marathi, Telugu, Tamil, Kannada, or English — making agricultural knowledge accessible to all.',
    screenshot: '/screenshots/voice-assistant.png',
    features: [
      'Hindi (हिंदी)',
      'Marathi (मराठी)',
      'Telugu (తెలుగు)',
      'Tamil (தமிழ்)',
      'Kannada (ಕನ್ನಡ)',
      'English',
    ],
  },
];

export default function ServicesPage() {
  return (
    <>
      {/* Hero Section */}
      <section className="bg-gradient-to-br from-primary/5 via-background to-secondary/5 py-16 md:py-24">
        <Container>
          <div className="text-center max-w-3xl mx-auto">
            <h1 className="text-4xl md:text-5xl font-bold text-gray-900 mb-6">
              Our Services
            </h1>
            <p className="text-lg text-gray-600">
              Krishi AI offers a comprehensive suite of tools designed to empower 
              Indian farmers with technology. Explore our features below.
            </p>
          </div>
        </Container>
      </section>

      {/* Services Grid */}
      <section className="py-16 md:py-24">
        <Container>
          <div className="space-y-16">
            {services.map((service, index) => (
              <div
                key={service.title}
                className={`grid lg:grid-cols-2 gap-8 lg:gap-12 items-center ${
                  index % 2 === 1 ? 'lg:flex-row-reverse' : ''
                }`}
              >
                <div className={index % 2 === 1 ? 'lg:order-2' : ''}>
                  <div className="flex items-center gap-4 mb-4">
                    <div className="w-14 h-14 bg-primary/10 rounded-xl flex items-center justify-center text-primary">
                      <service.icon className="w-7 h-7" />
                    </div>
                    <h2 className="text-2xl md:text-3xl font-bold text-gray-900">
                      {service.title}
                    </h2>
                  </div>
                  <p className="text-gray-600 mb-6 text-lg">
                    {service.description}
                  </p>
                  <ul className="space-y-3">
                    {service.features.map((feature) => (
                      <li key={feature} className="flex items-center gap-3">
                        <div className="w-5 h-5 bg-primary/20 rounded-full flex items-center justify-center flex-shrink-0">
                          <div className="w-2 h-2 bg-primary rounded-full"></div>
                        </div>
                        <span className="text-gray-700">{feature}</span>
                      </li>
                    ))}
                  </ul>
                </div>
                
                {/* Screenshot */}
                <div className={`${index % 2 === 1 ? 'lg:order-1' : ''}`}>
                  <div className="relative mx-auto max-w-xs">
                    <div className="relative rounded-[2rem] overflow-hidden shadow-2xl border-4 border-gray-900 bg-gray-900">
                      <Image
                        src={service.screenshot}
                        alt={`${service.title} Screenshot`}
                        width={280}
                        height={600}
                        className="w-full h-auto"
                      />
                    </div>
                    {/* Decorative shadow */}
                    <div className="absolute -inset-4 bg-gradient-to-br from-primary/20 to-secondary/20 rounded-[3rem] -z-10 blur-2xl"></div>
                  </div>
                </div>
              </div>
            ))}
          </div>
        </Container>
      </section>

      {/* CTA Section */}
      <section className="py-16 md:py-24 bg-gray-50">
        <Container>
          <div className="text-center">
            <h2 className="text-3xl md:text-4xl font-bold text-gray-900 mb-4">
              Experience All Features
            </h2>
            <p className="text-lg text-gray-600 mb-8 max-w-2xl mx-auto">
              Download Krishi AI today and unlock the full potential of AI-powered farming assistance.
            </p>
            <Button
              href="https://play.google.com/store/apps/details?id=com.maswadkar.androidxy"
              external
              size="lg"
            >
              <Smartphone className="w-5 h-5 mr-2" />
              Download on Play Store
            </Button>
          </div>
        </Container>
      </section>
    </>
  );
}
