import { CurrencyPipe, DatePipe, isPlatformBrowser } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectorRef, Component, DestroyRef, inject, OnInit, PLATFORM_ID } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { EMPTY, exhaustMap, switchMap, takeWhile, timer, catchError } from 'rxjs';
import { Order, isOrderPending } from '../../../../core/models/order.model';
import { OrdersService } from '../../services/orders.service';
import { OrderStatusComponent } from '../order-status/order-status';

@Component({
  selector: 'app-order-details',
  standalone: true,
  imports: [CurrencyPipe, DatePipe, RouterLink, OrderStatusComponent],
  templateUrl: './order-details.html',
  styleUrl: './order-details.scss',
})
export class OrderDetailsComponent implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly ordersService = inject(OrdersService);
  private readonly changeDetector = inject(ChangeDetectorRef);
  private readonly destroyRef = inject(DestroyRef);
  private readonly platformId = inject(PLATFORM_ID);

  protected order?: Order;
  protected loading = true;
  protected error = '';
  protected created = false;

  ngOnInit(): void {
    if (!isPlatformBrowser(this.platformId)) return;
    this.created = Boolean(history.state?.created);
    const id = this.route.snapshot.paramMap.get('id');
    if (!id) {
      this.error = 'Identificador do pedido inválido.';
      this.loading = false;
      this.changeDetector.markForCheck();
      return;
    }

    timer(0, 3000).pipe(
      exhaustMap(() => this.ordersService.get(id)),
      takeWhile((order) => isOrderPending(order.status), true),
      catchError((error: unknown) => {
        this.error = this.errorMessage(error);
        this.loading = false;
        this.changeDetector.markForCheck();
        return EMPTY;
      }),
      takeUntilDestroyed(this.destroyRef),
    ).subscribe((order) => {
      this.order = order;
      this.loading = false;
      this.error = '';
      this.changeDetector.markForCheck();
    });
  }

  protected stepClass(status: 'RECEIVED' | 'PROCESSING' | 'COMPLETED'): string {
    const order = this.order;
    if (!order) return '';
    if (order.status === 'FAILED') return status === 'RECEIVED' ? 'step-done' : status === 'PROCESSING' ? 'step-failed' : '';
    const steps = ['RECEIVED', 'PROCESSING', 'COMPLETED'];
    const current = steps.indexOf(order.status);
    return steps.indexOf(status) <= current ? 'step-done' : '';
  }

  private errorMessage(error: unknown): string {
    if (error instanceof HttpErrorResponse && error.status === 0) {
      return 'Não foi possível conectar à API. Verifique se o backend está em execução.';
    }
    if (error instanceof HttpErrorResponse) {
      return error.error?.detail ?? error.error?.title ?? 'Não foi possível carregar o pedido.';
    }
    return 'Ocorreu um erro inesperado ao carregar o pedido.';
  }
}
