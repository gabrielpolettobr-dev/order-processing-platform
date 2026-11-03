export type OrderStatus = 'RECEIVED' | 'PROCESSING' | 'COMPLETED' | 'FAILED';
export type OrderSort = 'createdAt,desc' | 'createdAt,asc' | 'totalAmount,desc' | 'customerName,asc';

export interface OrderItem {
  id: string;
  description: string;
  quantity: number;
  unitPrice: number;
  subtotal: number;
}

export interface Order {
  id: string;
  customerName: string;
  customerEmail: string;
  totalAmount: number;
  status: OrderStatus;
  createdAt: string;
  updatedAt: string;
  items: OrderItem[];
}

export interface CreateOrderItem {
  description: string;
  quantity: number;
  unitPrice: number;
}

export interface CreateOrder {
  customerName: string;
  customerEmail: string;
  items: CreateOrderItem[];
}

export interface Page<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  size: number;
  number: number;
  first: boolean;
  last: boolean;
}

export interface OrderFilters {
  status?: OrderStatus;
  createdFrom?: string;
  createdTo?: string;
  page: number;
  size: number;
  sort: OrderSort;
}

export function isOrderPending(status: OrderStatus): boolean {
  return status === 'RECEIVED' || status === 'PROCESSING';
}

export function orderStatusLabel(status: OrderStatus): string {
  const labels: Record<OrderStatus, string> = {
    RECEIVED: 'Aguardando',
    PROCESSING: 'Em processamento',
    COMPLETED: 'Concluído',
    FAILED: 'Falhou',
  };
  return labels[status];
}
