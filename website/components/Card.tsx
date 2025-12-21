import { ReactNode } from 'react';

interface CardProps {
  children: ReactNode;
  className?: string;
  hover?: boolean;
}

export function Card({ children, className = '', hover = true }: CardProps) {
  return (
    <div
      className={`
        bg-white rounded-2xl p-6 shadow-sm border border-gray-100
        ${hover ? 'hover:shadow-lg hover:border-primary/20 transition-all duration-300' : ''}
        ${className}
      `}
    >
      {children}
    </div>
  );
}

interface CardIconProps {
  children: ReactNode;
  className?: string;
}

export function CardIcon({ children, className = '' }: CardIconProps) {
  return (
    <div className={`w-14 h-14 rounded-xl bg-primary/10 flex items-center justify-center text-primary mb-4 ${className}`}>
      {children}
    </div>
  );
}

interface CardTitleProps {
  children: ReactNode;
  className?: string;
}

export function CardTitle({ children, className = '' }: CardTitleProps) {
  return (
    <h3 className={`text-xl font-semibold text-gray-900 mb-2 ${className}`}>
      {children}
    </h3>
  );
}

interface CardDescriptionProps {
  children: ReactNode;
  className?: string;
}

export function CardDescription({ children, className = '' }: CardDescriptionProps) {
  return (
    <p className={`text-gray-600 leading-relaxed ${className}`}>
      {children}
    </p>
  );
}
