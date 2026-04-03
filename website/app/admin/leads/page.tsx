import { Metadata } from 'next';
import AdminLeadsClient from './AdminLeadsClient';

export const metadata: Metadata = {
  title: 'Admin Leads - Krishi AI',
  description: 'Internal lead queue for app-created best-offer sales leads.',
};

export default function AdminLeadsPage() {
  return <AdminLeadsClient />;
}