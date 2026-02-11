import { Metadata } from 'next';
import { Container } from '@/components';

export const metadata: Metadata = {
  title: 'Privacy Policy - Krishi AI',
  description: 'Privacy Policy for Krishi AI app and website.',
};

export default function PrivacyPage() {
  return (
    <>
      {/* Hero Section */}
      <section className="bg-gradient-to-br from-primary/5 via-background to-secondary/5 py-16 md:py-20">
        <Container>
          <div className="text-center max-w-3xl mx-auto">
            <h1 className="text-4xl md:text-5xl font-bold text-gray-900 mb-4">
              Privacy Policy
            </h1>
            <p className="text-gray-600">
              Last updated: December 2025
            </p>
          </div>
        </Container>
      </section>

      {/* Content */}
      <section className="py-16 md:py-20">
        <Container>
          <div className="max-w-3xl mx-auto prose prose-gray prose-lg">
            <div className="space-y-8">
              <div>
                <h2 className="text-2xl font-bold text-gray-900 mb-4">Introduction</h2>
                <p className="text-gray-600">
                  Krishi AI (&quot;we,&quot; &quot;our,&quot; or &quot;us&quot;) is committed to protecting your privacy. 
                  This Privacy Policy explains how we collect, use, disclose, and safeguard your information 
                  when you use our mobile application and website.
                </p>
              </div>

              <div>
                <h2 className="text-2xl font-bold text-gray-900 mb-4">Information We Collect</h2>
                <h3 className="text-xl font-semibold text-gray-800 mb-2">Personal Information</h3>
                <p className="text-gray-600 mb-4">
                  When you use Krishi AI, we may collect:
                </p>
                <ul className="list-disc pl-6 text-gray-600 space-y-2">
                  <li>Google account information (name, email) when you sign in</li>
                  <li>Chat conversations and queries you make to the AI assistant</li>
                  <li>Images you upload for plant diagnosis</li>
                  <li>Your Mandi location preferences</li>
                </ul>

                <h3 className="text-xl font-semibold text-gray-800 mb-2 mt-6">Automatically Collected Information</h3>
                <ul className="list-disc pl-6 text-gray-600 space-y-2">
                  <li>Device information (model, operating system)</li>
                  <li>App usage statistics</li>
                  <li>Crash reports and performance data</li>
                </ul>
              </div>

              <div>
                <h2 className="text-2xl font-bold text-gray-900 mb-4">How We Use Your Information</h2>
                <p className="text-gray-600 mb-4">We use the collected information to:</p>
                <ul className="list-disc pl-6 text-gray-600 space-y-2">
                  <li>Provide AI-powered farming assistance and advice</li>
                  <li>Process and analyze crop images for plant diagnosis</li>
                  <li>Save your chat history for future reference</li>
                  <li>Display relevant Mandi prices based on your preferences</li>
                  <li>Improve our services and user experience</li>
                  <li>Send important updates about the app (if you opt-in)</li>
                </ul>
              </div>

              <div>
                <h2 className="text-2xl font-bold text-gray-900 mb-4">Data Storage and Security</h2>
                <p className="text-gray-600">
                  Your data is stored securely using Google Firebase services. We implement appropriate 
                  technical and organizational security measures to protect your personal information. 
                  However, no method of transmission over the Internet is 100% secure.
                </p>
              </div>

              <div>
                <h2 className="text-2xl font-bold text-gray-900 mb-4">Third-Party Services</h2>
                <p className="text-gray-600 mb-4">We use the following third-party services:</p>
                <ul className="list-disc pl-6 text-gray-600 space-y-2">
                  <li><strong>Google Firebase:</strong> Authentication, database, and AI services</li>
                  <li><strong>Google Gemini AI:</strong> Powers our AI chat assistant</li>
                  <li><strong>Government of India APIs:</strong> Mandi price data</li>
                </ul>
                <p className="text-gray-600 mt-4">
                  These services have their own privacy policies governing the use of your information.
                </p>
              </div>

              <div>
                <h2 className="text-2xl font-bold text-gray-900 mb-4">Your Rights</h2>
                <p className="text-gray-600 mb-4">You have the right to:</p>
                <ul className="list-disc pl-6 text-gray-600 space-y-2">
                  <li>Access your personal data</li>
                  <li>Request deletion of your data</li>
                  <li>Opt-out of non-essential data collection</li>
                  <li>Export your chat history</li>
                </ul>
                <p className="text-gray-600 mt-4">
                  To exercise these rights, contact us at support@krishiai.pro.
                </p>
              </div>

              <div>
                <h2 className="text-2xl font-bold text-gray-900 mb-4">Children&apos;s Privacy</h2>
                <p className="text-gray-600">
                  Krishi AI is not intended for children under 13. We do not knowingly collect 
                  personal information from children under 13. If you believe we have collected 
                  such information, please contact us immediately.
                </p>
              </div>

              <div>
                <h2 className="text-2xl font-bold text-gray-900 mb-4">Changes to This Policy</h2>
                <p className="text-gray-600">
                  We may update this Privacy Policy from time to time. We will notify you of any 
                  changes by posting the new policy on this page and updating the &quot;Last updated&quot; date.
                </p>
              </div>

              <div>
                <h2 className="text-2xl font-bold text-gray-900 mb-4">Contact Us</h2>
                <p className="text-gray-600">
                  If you have questions about this Privacy Policy, please contact us at:
                </p>
                <ul className="list-none text-gray-600 mt-2 space-y-1">
                  <li>Email: support@krishiai.pro</li>
                  <li>WhatsApp: +91 94035 13382</li>
                </ul>
              </div>
            </div>
          </div>
        </Container>
      </section>
    </>
  );
}
