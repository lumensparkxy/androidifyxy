import Image from 'next/image';
import { Bot, Mic, Camera, TrendingUp, Smartphone, Globe, Shield, Zap } from 'lucide-react';
import { Container, SectionHeading, Card, CardIcon, CardTitle, CardDescription, Button, PlayStoreBadge } from '@/components';

const features = [
  {
    icon: Bot,
    title: 'AI Chat Assistant',
    description: 'Get instant answers to all your farming questions powered by advanced AI technology.',
  },
  {
    icon: Mic,
    title: 'Voice Conversations',
    description: 'Speak naturally in Hindi, Marathi, or English and get voice responses.',
  },
  {
    icon: Camera,
    title: 'Crop Disease Diagnosis',
    description: 'Take a photo of your crop and get instant disease identification and treatment advice.',
  },
  {
    icon: TrendingUp,
    title: 'Live Mandi Prices',
    description: 'Check real-time market prices for agricultural commodities across Maharashtra.',
  },
];

const benefits = [
  {
    icon: Globe,
    title: 'Multilingual Support',
    description: 'Available in Hindi, Marathi, Telugu, Tamil, Kannada, and English.',
  },
  {
    icon: Smartphone,
    title: 'Easy to Use',
    description: 'Simple, intuitive interface designed for farmers of all ages.',
  },
  {
    icon: Shield,
    title: 'Trusted Information',
    description: 'AI trained on agricultural best practices and government data.',
  },
  {
    icon: Zap,
    title: 'Instant Responses',
    description: 'Get answers in seconds, anytime, anywhere.',
  },
];

export default function HomePage() {
  return (
    <>
      {/* Hero Section */}
      <section className="relative overflow-hidden bg-gradient-to-br from-primary/5 via-background to-secondary/5 py-16 md:py-24">
        <Container>
          <div className="grid lg:grid-cols-2 gap-12 items-center">
            <div className="text-center lg:text-left">
              <div className="inline-flex items-center gap-2 bg-primary/10 text-primary px-4 py-2 rounded-full text-sm font-medium mb-6">
                <span className="w-2 h-2 bg-primary rounded-full animate-pulse"></span>
                AI-Powered Farming Assistant
              </div>
              <h1 className="text-4xl md:text-5xl lg:text-6xl font-bold text-gray-900 mb-6 leading-tight">
                Your Smart
                <span className="text-primary"> Farming</span>
                <br />Companion
              </h1>
              <p className="text-lg md:text-xl text-gray-600 mb-8 max-w-xl mx-auto lg:mx-0">
                Krishi AI (कृषि AI) helps Indian farmers with AI-powered advice, 
                live market prices, crop disease diagnosis, and more — all in your language.
              </p>
              <div className="flex flex-col sm:flex-row gap-4 justify-center lg:justify-start">
                <PlayStoreBadge href="https://play.google.com/store/apps/details?id=com.maswadkar.androidxy" />
                <Button href="/services" variant="outline" size="lg">
                  Explore Features
                </Button>
              </div>
            </div>
            
            {/* Hero Image */}
            <div className="relative">
              <div className="relative mx-auto w-64 md:w-80">
                {/* Phone Screenshot */}
                <div className="relative rounded-[2rem] overflow-hidden shadow-2xl border-4 border-gray-900 bg-gray-900">
                  <Image
                    src="/screenshots/hero-screenshot.png"
                    alt="Krishi AI App Screenshot"
                    width={320}
                    height={680}
                    className="w-full h-auto"
                    priority
                  />
                </div>
                {/* Decorative Elements */}
                <div className="absolute -top-4 -right-4 w-20 h-20 bg-secondary/20 rounded-full blur-xl"></div>
                <div className="absolute -bottom-4 -left-4 w-32 h-32 bg-primary/20 rounded-full blur-xl"></div>
              </div>
            </div>
          </div>
        </Container>
      </section>

      {/* Features Section */}
      <section className="py-16 md:py-24">
        <Container>
          <SectionHeading
            title="Powerful Features"
            subtitle="Everything you need to make informed farming decisions, right at your fingertips."
          />
          <div className="grid sm:grid-cols-2 lg:grid-cols-4 gap-6">
            {features.map((feature) => (
              <Card key={feature.title}>
                <CardIcon>
                  <feature.icon className="w-7 h-7" />
                </CardIcon>
                <CardTitle>{feature.title}</CardTitle>
                <CardDescription>{feature.description}</CardDescription>
              </Card>
            ))}
          </div>
        </Container>
      </section>

      {/* Benefits Section */}
      <section className="py-16 md:py-24 bg-gray-50">
        <Container>
          <SectionHeading
            title="Why Choose Krishi AI?"
            subtitle="Built specifically for Indian farmers, with features that matter most."
          />
          <div className="grid sm:grid-cols-2 lg:grid-cols-4 gap-6">
            {benefits.map((benefit) => (
              <div key={benefit.title} className="text-center p-6">
                <div className="w-14 h-14 bg-primary/10 rounded-xl flex items-center justify-center mx-auto mb-4 text-primary">
                  <benefit.icon className="w-7 h-7" />
                </div>
                <h3 className="text-lg font-semibold text-gray-900 mb-2">{benefit.title}</h3>
                <p className="text-gray-600 text-sm">{benefit.description}</p>
              </div>
            ))}
          </div>
        </Container>
      </section>

      {/* CTA Section */}
      <section className="py-16 md:py-24 bg-primary">
        <Container>
          <div className="text-center text-white">
            <h2 className="text-3xl md:text-4xl font-bold mb-4">
              Ready to Transform Your Farming?
            </h2>
            <p className="text-lg text-white/80 mb-8 max-w-2xl mx-auto">
              Join thousands of farmers who are already using Krishi AI to get better yields, 
              fair prices, and expert advice.
            </p>
            <PlayStoreBadge href="https://play.google.com/store/apps/details?id=com.maswadkar.androidxy" />
          </div>
        </Container>
      </section>

      {/* Stats Section */}
      <section className="py-16 md:py-20">
        <Container>
          <div className="grid grid-cols-2 md:grid-cols-4 gap-8 text-center">
            <div>
              <p className="text-4xl md:text-5xl font-bold text-primary mb-2">6+</p>
              <p className="text-gray-600">Languages Supported</p>
            </div>
            <div>
              <p className="text-4xl md:text-5xl font-bold text-primary mb-2">24/7</p>
              <p className="text-gray-600">AI Assistance</p>
            </div>
            <div>
              <p className="text-4xl md:text-5xl font-bold text-primary mb-2">100+</p>
              <p className="text-gray-600">Commodities Tracked</p>
            </div>
            <div>
              <p className="text-4xl md:text-5xl font-bold text-primary mb-2">Free</p>
              <p className="text-gray-600">To Download</p>
            </div>
          </div>
        </Container>
      </section>
    </>
  );
}
