import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { CreateOrder, Order, OrderFilters, Page } from '../../../core/models/order.model';

@Injectable({ providedIn: 'root' })
export class OrdersService {
  private static readonly requestTimeoutMs = 10_000;
  private readonly http = inject(HttpClient);
  private readonly endpoint = '/api/orders';

  list(filters: OrderFilters): Observable<Page<Order>> {
    let params = new HttpParams()
      .set('page', filters.page)
      .set('size', filters.size)
      .set('sort', filters.sort);

    if (filters.status) params = params.set('status', filters.status);
    if (filters.createdFrom) params = params.set('createdFrom', filters.createdFrom);
    if (filters.createdTo) params = params.set('createdTo', filters.createdTo);

    return this.http.get<Page<Order>>(this.endpoint, { params, timeout: OrdersService.requestTimeoutMs });
  }

  get(id: string): Observable<Order> {
    return this.http.get<Order>(`${this.endpoint}/${encodeURIComponent(id)}`, {
      timeout: OrdersService.requestTimeoutMs,
    });
  }

  create(order: CreateOrder): Observable<Order> {
    return this.http.post<Order>(this.endpoint, order, { timeout: OrdersService.requestTimeoutMs });
  }
}
