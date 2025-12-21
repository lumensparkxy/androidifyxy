import { Metadata } from 'next';
import { Container } from '@/components';

export const metadata: Metadata = {
  title: 'Terms of Service - Krishi AI',
  description: 'Terms of Service for Krishi AI app and website.',
};

export default function TermsPage() {
  return (
    <>
      {/* Hero Section */}
      <section className="bg-gradient-to-br from-primary/5 via-background to-secondary/5 py-16 md:py-20">
        <Container>
          <div className="text-center max-w-3xl mx-auto">
            <h1 className="text-4xl md:text-5xl font-bold text-gray-900 mb-4">
              Terms of Service
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
                <h2 className="text-2xl font-bold text-gray-900 mb-4">Acceptance of Terms</h2>
                <p className="text-gray-600">
                  By downloading, installing, or using Krishi AI (&quot;the App&quot;), you agree to be 
                  bound by these Terms of Service. If you do not agree to these terms, please do 
                  not use the App.
                </p>
              </div>

              <div>
                <h2 className="text-2xl font-bold text-gray-900 mb-4">Description of Service</h2>
                <p className="text-gray-600">
                  Krishi AI is an AI-powered agricultural assistant application that provides:
                </p>
                <ul className="list-disc pl-6 text-gray-600 space-y-2 mt-2">
                  <li>AI-based farming advice and information</li>
                  <li>Crop disease identification through image analysis</li>
                  <li>Live agricultural commodity prices (Mandi prices)</li>
                  <li>Voice-based conversational assistance</li>
                  <li>Multilingual support for Indian languages</li>
                </ul>
              </div>

              <div>
                <h2 className="text-2xl font-bold text-gray-900 mb-4">User Responsibilities</h2>
                <p className="text-gray-600 mb-4">By using Krishi AI, you agree to:</p>
                <ul className="list-disc pl-6 text-gray-600 space-y-2">
                  <li>Provide accurate information when creating an account</li>
                  <li>Use the App only for lawful agricultural purposes</li>
                  <li>Not attempt to reverse engineer or modify the App</li>
                  <li>Not use the App to distribute harmful or malicious content</li>
                  <li>Respect the intellectual property rights of Maswadkar Developers</li>
                </ul>
              </div>

              <div>
                <h2 className="text-2xl font-bold text-gray-900 mb-4">Disclaimer of Warranties</h2>
                <p className="text-gray-600 mb-4">
                  <strong>Important:</strong> The information provided by Krishi AI is for 
                  general informational purposes only.
                </p>
                <ul className="list-disc pl-6 text-gray-600 space-y-2">
                  <li>
                    <strong>AI Advice:</strong> The AI assistant provides suggestions based on 
                    general agricultural knowledge. It should not replace professional agronomist 
                    advice for critical decisions.
                  </li>
                  <li>
                    <strong>Disease Diagnosis:</strong> Crop disease identification is AI-based 
                    and may not be 100% accurate. For serious crop issues, please consult local 
                    agricultural experts.
                  </li>
                  <li>
                    <strong>Mandi Prices:</strong> While we strive to provide accurate market prices, 
                    data may be delayed or contain errors. Always verify prices at the actual mandi 
                    before making selling decisions.
                  </li>
                </ul>
              </div>

              <div>
                <h2 className="text-2xl font-bold text-gray-900 mb-4">Limitation of Liability</h2>
                <p className="text-gray-600">
                  To the maximum extent permitted by law, Maswadkar Developers and its affiliates 
                  shall not be liable for any indirect, incidental, special, consequential, or 
                  punitive damages, including but not limited to:
                </p>
                <ul className="list-disc pl-6 text-gray-600 space-y-2 mt-2">
                  <li>Loss of crops or agricultural produce</li>
                  <li>Financial losses from market decisions</li>
                  <li>Damages from incorrect disease identification</li>
                  <li>Any other losses arising from use of the App</li>
                </ul>
              </div>

              <div>
                <h2 className="text-2xl font-bold text-gray-900 mb-4">Intellectual Property</h2>
                <p className="text-gray-600">
                  All content, features, and functionality of Krishi AI, including but not 
                  limited to text, graphics, logos, icons, images, and software, are the exclusive 
                  property of Maswadkar Developers and are protected by copyright, trademark, and 
                  other intellectual property laws.
                </p>
              </div>

              <div>
                <h2 className="text-2xl font-bold text-gray-900 mb-4">Third-Party Services</h2>
                <p className="text-gray-600">
                  Krishi AI uses third-party services including Google Firebase and government 
                  APIs. Your use of these services is subject to their respective terms of service 
                  and privacy policies.
                </p>
              </div>

              <div>
                <h2 className="text-2xl font-bold text-gray-900 mb-4">Modifications to Service</h2>
                <p className="text-gray-600">
                  We reserve the right to modify, suspend, or discontinue any part of the App at 
                  any time without prior notice. We may also update these Terms of Service from 
                  time to time. Continued use of the App after changes constitutes acceptance of 
                  the modified terms.
                </p>
              </div>

              <div>
                <h2 className="text-2xl font-bold text-gray-900 mb-4">Termination</h2>
                <p className="text-gray-600">
                  We may terminate or suspend your access to the App immediately, without prior 
                  notice, for any reason, including breach of these Terms. Upon termination, your 
                  right to use the App will cease immediately.
                </p>
              </div>

              <div>
                <h2 className="text-2xl font-bold text-gray-900 mb-4">Governing Law</h2>
                <p className="text-gray-600">
                  These Terms shall be governed by and construed in accordance with the laws of 
                  India, without regard to its conflict of law provisions. Any disputes arising 
                  from these Terms shall be subject to the exclusive jurisdiction of the courts 
                  in Maharashtra, India.
                </p>
              </div>

              <div>
                <h2 className="text-2xl font-bold text-gray-900 mb-4">Contact Information</h2>
                <p className="text-gray-600">
                  For questions about these Terms of Service, please contact us at:
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
