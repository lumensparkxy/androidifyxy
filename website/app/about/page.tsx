import { Metadata } from 'next';
import Image from 'next/image';
import { Target, Eye, Heart, Users, Lightbulb, Award } from 'lucide-react';
import { Container, SectionHeading, Card, CardIcon, CardTitle, CardDescription, Button } from '@/components';

export const metadata: Metadata = {
  title: 'About Us - Krishi AI',
  description: 'Learn about Krishi AI\'s mission to empower Indian farmers with AI technology. Built by Maswadkar Developers.',
};

const values = [
  {
    icon: Heart,
    title: 'Farmer First',
    description: 'Every feature we build is designed with the Indian farmer in mind — simple, useful, and accessible.',
  },
  {
    icon: Lightbulb,
    title: 'Innovation',
    description: 'We leverage cutting-edge AI technology to solve real agricultural challenges.',
  },
  {
    icon: Users,
    title: 'Inclusivity',
    description: 'Supporting multiple Indian languages to ensure no farmer is left behind.',
  },
  {
    icon: Award,
    title: 'Trust',
    description: 'Providing accurate, government-backed data and reliable AI assistance.',
  },
];

export default function AboutPage() {
  return (
    <>
      {/* Hero Section */}
      <section className="bg-gradient-to-br from-primary/5 via-background to-secondary/5 py-16 md:py-24">
        <Container>
          <div className="text-center max-w-3xl mx-auto">
            <h1 className="text-4xl md:text-5xl font-bold text-gray-900 mb-6">
              About Krishi AI
            </h1>
            <p className="text-lg text-gray-600">
              Empowering Indian farmers with the power of artificial intelligence
            </p>
          </div>
        </Container>
      </section>

      {/* Mission Section */}
      <section className="py-16 md:py-24">
        <Container>
          <div className="grid lg:grid-cols-2 gap-12 items-center">
            <div>
              <div className="flex items-center gap-3 mb-4">
                <div className="w-12 h-12 bg-primary/10 rounded-xl flex items-center justify-center text-primary">
                  <Target className="w-6 h-6" />
                </div>
                <h2 className="text-2xl md:text-3xl font-bold text-gray-900">Our Mission</h2>
              </div>
              <p className="text-gray-600 text-lg mb-6">
                Krishi AI was born from a simple observation: Indian farmers, the backbone of our nation, 
                often lack access to timely, accurate agricultural information in their own language.
              </p>
              <p className="text-gray-600 mb-6">
                Our mission is to bridge this gap by putting the power of AI in every farmer&apos;s pocket. 
                Whether it&apos;s advice on crop diseases, understanding market prices, or learning modern 
                farming techniques — Krishi AI is always there to help, in Hindi, Marathi, or any 
                language the farmer speaks.
              </p>
              <p className="text-gray-600">
                We believe that technology should be accessible to everyone, regardless of their education 
                level or technical expertise. That&apos;s why Krishi AI is designed to be as simple as 
                having a conversation with a knowledgeable friend.
              </p>
            </div>
            
            {/* App Screenshot */}
            <div className="relative mx-auto max-w-xs">
              <div className="relative rounded-[2rem] overflow-hidden shadow-2xl border-4 border-gray-900 bg-gray-900">
                <Image
                  src="/screenshots/hero-screenshot.png"
                  alt="Krishi AI App"
                  width={280}
                  height={600}
                  className="w-full h-auto"
                />
              </div>
              {/* Decorative shadow */}
              <div className="absolute -inset-4 bg-gradient-to-br from-primary/20 to-secondary/20 rounded-[3rem] -z-10 blur-2xl"></div>
            </div>
          </div>
        </Container>
      </section>

      {/* Vision Section */}
      <section className="py-16 md:py-24 bg-gray-50">
        <Container>
          <div className="grid lg:grid-cols-2 gap-12 items-center">
            {/* App Screenshot */}
            <div className="relative mx-auto max-w-xs lg:order-1">
              <div className="relative rounded-[2rem] overflow-hidden shadow-2xl border-4 border-gray-900 bg-gray-900">
                <Image
                  src="/screenshots/crop-scan.png"
                  alt="Krishi AI Crop Scan Feature"
                  width={280}
                  height={600}
                  className="w-full h-auto"
                />
              </div>
              {/* Decorative shadow */}
              <div className="absolute -inset-4 bg-gradient-to-br from-secondary/20 to-primary/20 rounded-[3rem] -z-10 blur-2xl"></div>
            </div>
            
            <div className="lg:order-2">
              <div className="flex items-center gap-3 mb-4">
                <div className="w-12 h-12 bg-secondary/10 rounded-xl flex items-center justify-center text-secondary">
                  <Eye className="w-6 h-6" />
                </div>
                <h2 className="text-2xl md:text-3xl font-bold text-gray-900">Our Vision</h2>
              </div>
              <p className="text-gray-600 text-lg mb-6">
                We envision a future where every Indian farmer has access to expert agricultural 
                knowledge at their fingertips, enabling them to make data-driven decisions and 
                improve their livelihoods.
              </p>
              <div className="space-y-4">
                <div className="flex items-start gap-3">
                  <div className="w-6 h-6 bg-primary rounded-full flex items-center justify-center flex-shrink-0 mt-0.5">
                    <span className="text-white text-xs font-bold">1</span>
                  </div>
                  <p className="text-gray-600">
                    <strong className="text-gray-900">Democratize agricultural knowledge</strong> — 
                    Make expert farming advice accessible to all, not just those who can afford consultants.
                  </p>
                </div>
                <div className="flex items-start gap-3">
                  <div className="w-6 h-6 bg-primary rounded-full flex items-center justify-center flex-shrink-0 mt-0.5">
                    <span className="text-white text-xs font-bold">2</span>
                  </div>
                  <p className="text-gray-600">
                    <strong className="text-gray-900">Increase farmer income</strong> — 
                    Help farmers get better prices by providing real-time market information.
                  </p>
                </div>
                <div className="flex items-start gap-3">
                  <div className="w-6 h-6 bg-primary rounded-full flex items-center justify-center flex-shrink-0 mt-0.5">
                    <span className="text-white text-xs font-bold">3</span>
                  </div>
                  <p className="text-gray-600">
                    <strong className="text-gray-900">Promote sustainable farming</strong> — 
                    Educate farmers on organic and sustainable practices for long-term benefits.
                  </p>
                </div>
              </div>
            </div>
          </div>
        </Container>
      </section>

      {/* Values Section */}
      <section className="py-16 md:py-24">
        <Container>
          <SectionHeading
            title="Our Values"
            subtitle="The principles that guide everything we do at Krishi AI."
          />
          <div className="grid sm:grid-cols-2 lg:grid-cols-4 gap-6">
            {values.map((value) => (
              <Card key={value.title}>
                <CardIcon>
                  <value.icon className="w-7 h-7" />
                </CardIcon>
                <CardTitle>{value.title}</CardTitle>
                <CardDescription>{value.description}</CardDescription>
              </Card>
            ))}
          </div>
        </Container>
      </section>

      {/* Team Section */}
      <section className="py-16 md:py-24 bg-gray-50">
        <Container>
          <div className="text-center max-w-3xl mx-auto">
            <h2 className="text-3xl md:text-4xl font-bold text-gray-900 mb-4">
              Built by Maswadkar Developers
            </h2>
            <p className="text-lg text-gray-600 mb-8">
              Krishi AI is developed by Maswadkar Developers, a team passionate about using 
              technology to solve real-world problems in India. We combine our expertise in 
              mobile development, AI, and agriculture to create tools that truly make a difference.
            </p>
            <div className="bg-white rounded-2xl p-8 shadow-sm border border-gray-100">
              <p className="text-gray-600 italic text-lg">
                &quot;We believe that the farmers who feed our nation deserve access to the best 
                technology available. Krishi AI is our contribution to making that vision a reality.&quot;
              </p>
              <p className="text-primary font-semibold mt-4">— Maswadkar Developers Team</p>
            </div>
          </div>
        </Container>
      </section>

      {/* CTA Section */}
      <section className="py-16 md:py-24 bg-primary">
        <Container>
          <div className="text-center text-white">
            <h2 className="text-3xl md:text-4xl font-bold mb-4">
              Join Our Mission
            </h2>
            <p className="text-lg text-white/80 mb-8 max-w-2xl mx-auto">
              Download Krishi AI and be part of the agricultural revolution in India.
            </p>
            <div className="flex flex-col sm:flex-row gap-4 justify-center">
              <Button
                href="https://play.google.com/store/apps/details?id=com.maswadkar.developers.androidify"
                external
                variant="secondary"
                size="lg"
                className="bg-white text-primary hover:bg-gray-100"
              >
                Download the App
              </Button>
              <Button href="/contact" variant="outline" size="lg" className="border-white text-white hover:bg-white hover:text-primary">
                Contact Us
              </Button>
            </div>
          </div>
        </Container>
      </section>
    </>
  );
}
