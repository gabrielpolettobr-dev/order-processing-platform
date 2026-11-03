import { DatePipe, CurrencyPipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectorRef, Component, DestroyRef, inject, OnInit, PLATFORM_ID } from '@angular/core';
import { isPlatformBrowser } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { EMPTY, exhaustMap, Subject, startWith, switchMap, takeWhile, timer, catchError } from 'rxjs';
import { Order, OrderFilters, OrderSort, OrderStatus, Page, isOrderPending } from '../../../../core/models/order.model';
import { OrdersService } from '../../services/orders.service';
import { OrderStatusComponent } from '../order-status/order-status';

@Component({
  selector: 'app-order-list',
  standalone: true,
  imports: [DatePipe, CurrencyPipe, FormsModule, RouterLink, OrderStatusComponent],
  templateUrl: './order-list.html',
  styleUrl: './order-list.scss',
})
export class OrderListComponent implements OnInit {
  private readonly ordersService = inject(OrdersService);
  private readonly changeDetector = inject(ChangeDetectorRef);
  private readonly destroyRef = inject(DestroyRef);
  private readonly platformId = inject(PLATFORM_ID);
  private readonly refreshRequests = new Subject<void>();

  protected orders: Order[] = [];
  protected page?: Page<Order>;
  protected loading = true;
  protected error = '';
  protected status = '';
  protected createdFrom = '';
  protected createdTo = '';
  protected sort: OrderSort = 'createdAt,desc';
  protected readonly pageSize = 10;

  ngOnInit(): void {
    if (!isPlatformBrowser(this.platformId)) return;

    this.refreshRequests.pipe(
      startWith(void 0),
      switchMap(() => timer(0, 3000).pipe(
        exhaustMap(() => this.ordersService.list(this.filters())),
        takeWhile((result) => result.content.some((order) => isOrderPending(order.status)), true),
        catchError((error: unknown) => {
          this.error = this.errorMessage(error);
          this.loading = false;
          this.changeDetector.markForCheck();
          return EMPTY;
        }),
      )),
      takeUntilDestroyed(this.destroyRef),
    ).subscribe((result) => {
      this.page = result;
      this.orders = result.content;
      this.loading = false;
      this.error = '';
      this.changeDetector.markForCheck();
    });
  }

  protected applyFilters(): void {
    if (this.page) this.page.number = 0;
    this.refreshRequests.next();
  }

  protected refresh(): void {
    this.loading = this.orders.length === 0;
    this.refreshRequests.next();
  }

  protected goToPage(pageNumber: number): void {
    if (!this.page || pageNumber < 0 || pageNumber >= this.page.totalPages) return;
    this.page.number = pageNumber;
    this.refreshRequests.next();
  }

  private filters(): OrderFilters {
    const page = this.page?.number ?? 0;
    return {
      status: this.status ? this.status as OrderStatus : undefined,
      createdFrom: this.dateFilterValue(this.createdFrom, false),
      createdTo: this.dateFilterValue(this.createdTo, true),
      page,
      size: this.pageSize,
      sort: this.sort,
    };
  }

  private dateFilterValue(value: string, endOfDay: boolean): string | undefined {
    if (!value) return undefined;

    const [year, month, day] = value.split('-').map(Number);
    const date = new Date(0);
    date.setFullYear(year, month - 1, day);
    date.setHours(endOfDay ? 23 : 0, endOfDay ? 59 : 0, endOfDay ? 59 : 0, endOfDay ? 999 : 0);
    return date.toISOString();
  }

  private errorMessage(error: unknown): string {
    if (error instanceof HttpErrorResponse && error.status === 0) {
      return 'Não foi possível conectar à API. Verifique se o backend está em execução.';
    }
    if (error instanceof HttpErrorResponse) {
      return error.error?.detail ?? error.error?.title ?? 'Não foi possível carregar os pedidos.';
    }
    return 'Ocorreu um erro inesperado ao carregar os pedidos.';
  }
}
