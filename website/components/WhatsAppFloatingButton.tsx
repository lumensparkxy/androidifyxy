'use client';

import { MessageCircle } from 'lucide-react';

interface WhatsAppFloatingButtonProps {
  phoneNumber: string;
  message?: string;
}

export function WhatsAppFloatingButton({
  phoneNumber,
  message = 'Hi, I have a query about Krishi AI',
}: WhatsAppFloatingButtonProps) {
  const whatsappUrl = `https://wa.me/${phoneNumber}?text=${encodeURIComponent(message)}`;

  return (
    <a
      href={whatsappUrl}
      target="_blank"
      rel="noopener noreferrer"
      className="fixed bottom-6 right-6 z-50 w-14 h-14 bg-green-500 hover:bg-green-600 text-white rounded-full shadow-lg hover:shadow-xl transition-all duration-300 flex items-center justify-center group"
      aria-label="Contact us on WhatsApp"
    >
      <MessageCircle className="w-7 h-7" />
      <span className="absolute right-full mr-3 bg-gray-900 text-white text-sm px-3 py-2 rounded-lg opacity-0 group-hover:opacity-100 transition-opacity duration-200 whitespace-nowrap">
        Chat with us
      </span>
    </a>
  );
}
