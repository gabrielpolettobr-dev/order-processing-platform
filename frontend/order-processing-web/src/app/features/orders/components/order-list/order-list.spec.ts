import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of } from 'rxjs';
import { vi } from 'vitest';
import { Order, OrderFilters, Page } from '../../../../core/models/order.model';
import { OrdersService } from '../../services/orders.service';
import { OrderListComponent } from './order-list';

describe('OrderListComponent date filters', () => {
  const emptyPage: Page<Order> = {
    content: [],
    totalElements: 0,
    totalPages: 0,
    size: 10,
    number: 0,
    first: true,
    last: true,
  };
  const list = vi.fn((_filters: OrderFilters) => of(emptyPage));

  beforeEach(async () => {
    list.mockClear();
    await TestBed.configureTestingModule({
      imports: [OrderListComponent],
      providers: [
        provideRouter([]),
        { provide: OrdersService, useValue: { list } },
      ],
    }).compileComponents();
  });

  it('uses native date inputs and converts selected dates to API instants', async () => {
    const fixture = TestBed.createComponent(OrderListComponent);
    fixture.detectChanges();
    await new Promise((resolve) => setTimeout(resolve, 10));

    const inputs = fixture.nativeElement.querySelectorAll('input');
    expect(inputs[0].type).toBe('date');
    expect(inputs[0].lang).toBe('pt-BR');
    expect(inputs[1].type).toBe('date');

    inputs[0].value = '2026-01-31';
    inputs[0].dispatchEvent(new Event('input'));
    inputs[1].value = '2026-02-02';
    inputs[1].dispatchEvent(new Event('input'));
    fixture.detectChanges();
    await new Promise((resolve) => setTimeout(resolve, 10));

    const lastFilters = list.mock.calls.at(-1)?.[0];
    expect(lastFilters?.createdFrom).toBe(new Date(2026, 0, 31, 0, 0, 0, 0).toISOString());
    expect(lastFilters?.createdTo).toBe(new Date(2026, 1, 2, 23, 59, 59, 999).toISOString());
    fixture.destroy();
  });
});
