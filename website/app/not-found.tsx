import { Container, Button } from '@/components';
import { Home, ArrowLeft } from 'lucide-react';

export default function NotFound() {
  return (
    <section className="min-h-[60vh] flex items-center justify-center py-16">
      <Container>
        <div className="text-center">
          <h1 className="text-8xl md:text-9xl font-bold text-primary/20 mb-4">404</h1>
          <h2 className="text-2xl md:text-3xl font-bold text-gray-900 mb-4">
            Page Not Found
          </h2>
          <p className="text-gray-600 mb-8 max-w-md mx-auto">
            Sorry, we couldn&apos;t find the page you&apos;re looking for. 
            It might have been moved or doesn&apos;t exist.
          </p>
          <div className="flex flex-col sm:flex-row gap-4 justify-center">
            <Button href="/" variant="primary">
              <Home className="w-5 h-5 mr-2" />
              Go Home
            </Button>
            <Button href="javascript:history.back()" variant="outline">
              <ArrowLeft className="w-5 h-5 mr-2" />
              Go Back
            </Button>
          </div>
        </div>
      </Container>
    </section>
  );
}
