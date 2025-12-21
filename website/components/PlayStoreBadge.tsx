import Image from 'next/image';

interface PlayStoreBadgeProps {
  href: string;
  className?: string;
}

export function PlayStoreBadge({ href, className = '' }: PlayStoreBadgeProps) {
  return (
    <a
      href={href}
      target="_blank"
      rel="noopener noreferrer"
      className={`inline-block transition-transform hover:scale-105 ${className}`}
    >
      <img
        src="https://play.google.com/intl/en_us/badges/static/images/badges/en_badge_web_generic.png"
        alt="Get it on Google Play"
        width={200}
        height={60}
        className="h-14 w-auto"
      />
    </a>
  );
}
