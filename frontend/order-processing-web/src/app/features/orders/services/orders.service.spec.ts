import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { CreateOrder, Order } from '../../../core/models/order.model';
import { OrdersService } from './orders.service';

describe('OrdersService', () => {
  let service: OrdersService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(OrdersService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('requests a filtered, sorted page of orders', () => {
    service.list({
      status: 'PROCESSING',
      createdFrom: '2026-01-01T00:00:00.000Z',
      page: 2,
      size: 10,
      sort: 'createdAt,desc',
    }).subscribe();

    const request = http.expectOne((req) => req.url === '/api/orders');
    expect(request.request.params.get('status')).toBe('PROCESSING');
    expect(request.request.params.get('createdFrom')).toBe('2026-01-01T00:00:00.000Z');
    expect(request.request.params.get('page')).toBe('2');
    expect(request.request.params.get('size')).toBe('10');
    expect(request.request.params.get('sort')).toBe('createdAt,desc');
    request.flush({ content: [], totalElements: 0, totalPages: 0, size: 10, number: 2, first: false, last: true });
  });

  it('creates an order without sending a total', () => {
    const payload: CreateOrder = {
      customerName: 'Ana Silva',
      customerEmail: 'ana@example.com',
      items: [{ description: 'Teclado', quantity: 2, unitPrice: 50 }],
    };

    service.create(payload).subscribe();

    const request = http.expectOne('/api/orders');
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual(payload);
    expect(request.request.body.totalAmount).toBeUndefined();
    request.flush({ id: 'order-id' } as Order);
  });
});
