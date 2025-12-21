import { Metadata } from 'next';
import { Mail, MessageCircle, MapPin, Clock, HelpCircle } from 'lucide-react';
import { Container, SectionHeading, Card, CardIcon, CardTitle, CardDescription, Button } from '@/components';

export const metadata: Metadata = {
  title: 'Contact Us - Krishi AI',
  description: 'Get in touch with the Krishi AI team. We\'re here to help with your questions and feedback.',
};

const contactMethods = [
  {
    icon: Mail,
    title: 'Email Us',
    description: 'Send us an email and we\'ll respond within 24-48 hours.',
    action: 'support@krishiai.pro',
    href: 'mailto:support@krishiai.pro',
    buttonText: 'Send Email',
  },
  {
    icon: MessageCircle,
    title: 'WhatsApp',
    description: 'Chat with us directly on WhatsApp for quick support.',
    action: '+91 94035 13382',
    href: 'https://wa.me/919403513382?text=Hi, I have a query about Krishi AI',
    buttonText: 'Open WhatsApp',
    external: true,
  },
];

const faqs = [
  {
    question: 'Is Krishi AI free to use?',
    answer: 'Yes, Krishi AI is completely free to download and use. All features including AI chat, voice conversations, and Mandi prices are available at no cost.',
  },
  {
    question: 'Which languages does Krishi AI support?',
    answer: 'Krishi AI supports Hindi, Marathi, Telugu, Tamil, Kannada, and English. You can ask questions and receive answers in any of these languages.',
  },
  {
    question: 'How accurate is the crop disease diagnosis?',
    answer: 'Our AI model is trained on thousands of crop disease images and provides reliable identification. However, for serious issues, we recommend consulting local agricultural experts as well.',
  },
  {
    question: 'Where does the Mandi price data come from?',
    answer: 'Mandi prices are sourced from the Government of India\'s official agricultural marketing data (data.gov.in) and are updated hourly during market hours.',
  },
  {
    question: 'Can I use Krishi AI offline?',
    answer: 'Currently, Krishi AI requires an internet connection to work. We are working on offline features for future updates.',
  },
  {
    question: 'How can I provide feedback or report issues?',
    answer: 'You can email us at support@krishiai.pro or message us on WhatsApp. We value your feedback and use it to improve the app.',
  },
];

export default function ContactPage() {
  return (
    <>
      {/* Hero Section */}
      <section className="bg-gradient-to-br from-primary/5 via-background to-secondary/5 py-16 md:py-24">
        <Container>
          <div className="text-center max-w-3xl mx-auto">
            <h1 className="text-4xl md:text-5xl font-bold text-gray-900 mb-6">
              Contact Us
            </h1>
            <p className="text-lg text-gray-600">
              Have questions or feedback? We&apos;d love to hear from you. 
              Reach out through any of the channels below.
            </p>
          </div>
        </Container>
      </section>

      {/* Contact Methods */}
      <section className="py-16 md:py-24">
        <Container>
          <div className="grid md:grid-cols-2 gap-8 max-w-4xl mx-auto">
            {contactMethods.map((method) => (
              <Card key={method.title} className="text-center p-8">
                <CardIcon className="mx-auto">
                  <method.icon className="w-7 h-7" />
                </CardIcon>
                <CardTitle className="text-xl">{method.title}</CardTitle>
                <CardDescription className="mb-4">{method.description}</CardDescription>
                <p className="text-primary font-semibold mb-4">{method.action}</p>
                <Button
                  href={method.href}
                  external={method.external}
                  variant="outline"
                >
                  {method.buttonText}
                </Button>
              </Card>
            ))}
          </div>
        </Container>
      </section>

      {/* Additional Info */}
      <section className="py-16 md:py-24 bg-gray-50">
        <Container>
          <div className="grid md:grid-cols-2 gap-8 max-w-4xl mx-auto">
            <div className="bg-white rounded-2xl p-8 border border-gray-100">
              <div className="flex items-center gap-3 mb-4">
                <div className="w-10 h-10 bg-primary/10 rounded-lg flex items-center justify-center text-primary">
                  <Clock className="w-5 h-5" />
                </div>
                <h3 className="text-lg font-semibold text-gray-900">Response Time</h3>
              </div>
              <p className="text-gray-600">
                We typically respond to emails within 24-48 hours. For urgent queries, 
                WhatsApp is the fastest way to reach us.
              </p>
            </div>
            
            <div className="bg-white rounded-2xl p-8 border border-gray-100">
              <div className="flex items-center gap-3 mb-4">
                <div className="w-10 h-10 bg-primary/10 rounded-lg flex items-center justify-center text-primary">
                  <MapPin className="w-5 h-5" />
                </div>
                <h3 className="text-lg font-semibold text-gray-900">Location</h3>
              </div>
              <p className="text-gray-600">
                Maswadkar Developers<br />
                Maharashtra, India
              </p>
            </div>
          </div>
        </Container>
      </section>

      {/* FAQ Section */}
      <section className="py-16 md:py-24">
        <Container>
          <SectionHeading
            title="Frequently Asked Questions"
            subtitle="Find answers to common questions about Krishi AI."
          />
          <div className="max-w-3xl mx-auto">
            <div className="space-y-4">
              {faqs.map((faq, index) => (
                <div
                  key={index}
                  className="bg-white rounded-xl p-6 border border-gray-100 hover:border-primary/20 hover:shadow-sm transition-all"
                >
                  <div className="flex items-start gap-4">
                    <div className="w-8 h-8 bg-primary/10 rounded-lg flex items-center justify-center flex-shrink-0">
                      <HelpCircle className="w-4 h-4 text-primary" />
                    </div>
                    <div>
                      <h3 className="font-semibold text-gray-900 mb-2">{faq.question}</h3>
                      <p className="text-gray-600">{faq.answer}</p>
                    </div>
                  </div>
                </div>
              ))}
            </div>
          </div>
        </Container>
      </section>

      {/* CTA Section */}
      <section className="py-16 md:py-24 bg-primary">
        <Container>
          <div className="text-center text-white">
            <h2 className="text-3xl md:text-4xl font-bold mb-4">
              Ready to Get Started?
            </h2>
            <p className="text-lg text-white/80 mb-8 max-w-2xl mx-auto">
              Download Krishi AI now and experience AI-powered farming assistance.
            </p>
            <Button
              href="https://play.google.com/store/apps/details?id=com.maswadkar.androidxy"
              external
              size="lg"
              className="bg-white text-primary hover:bg-gray-100"
            >
              Download on Play Store
            </Button>
          </div>
        </Container>
      </section>
    </>
  );
}
