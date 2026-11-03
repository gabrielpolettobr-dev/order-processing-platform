import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectorRef, Component, inject } from '@angular/core';
import { FormArray, FormBuilder, FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { OrdersService } from '../../services/orders.service';

type OrderItemForm = FormGroup<{
  description: FormControl<string>;
  quantity: FormControl<number>;
  unitPrice: FormControl<number>;
}>;

@Component({
  selector: 'app-order-form',
  standalone: true,
  imports: [ReactiveFormsModule, RouterLink],
  templateUrl: './order-form.html',
  styleUrl: './order-form.scss',
})
export class OrderFormComponent {
  private readonly formBuilder = inject(FormBuilder).nonNullable;
  private readonly ordersService = inject(OrdersService);
  private readonly changeDetector = inject(ChangeDetectorRef);
  private readonly router = inject(Router);

  protected readonly form = this.formBuilder.group({
    customerName: ['', [
      Validators.required,
      Validators.maxLength(200),
      Validators.pattern(/^[\p{L}\p{M}][\p{L}\p{M} '-]*$/u),
    ]],
    customerEmail: ['', [Validators.required, Validators.email, Validators.maxLength(320)]],
    items: this.formBuilder.array([this.createItem()]),
  });
  protected submitting = false;
  protected error = '';

  protected get items(): FormArray<OrderItemForm> {
    return this.form.controls.items;
  }

  protected get total(): number {
    return this.items.controls.reduce((sum, item) => {
      const quantity = Number(item.controls.quantity.value) || 0;
      const unitPrice = Number(item.controls.unitPrice.value) || 0;
      return sum + quantity * unitPrice;
    }, 0);
  }

  protected addItem(): void {
    this.items.push(this.createItem());
  }

  protected removeItem(index: number): void {
    if (this.items.length > 1) this.items.removeAt(index);
  }

  protected itemSubtotal(item: OrderItemForm): number {
    return item.controls.quantity.value * item.controls.unitPrice.value || 0;
  }

  protected formatCurrency(value: number): string {
    return new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' }).format(value);
  }

  protected limitQuantity(event: Event, item: OrderItemForm): void {
    const input = event.currentTarget as HTMLInputElement;
    const value = Number(input.value);
    if (!input.value || !Number.isFinite(value)) return;

    const limited = Math.min(Math.trunc(value), 1_000_000);
    if (value !== limited) {
      input.value = String(limited);
      item.controls.quantity.setValue(limited);
    }
  }

  protected limitUnitPrice(event: Event, item: OrderItemForm): void {
    const input = event.currentTarget as HTMLInputElement;
    if (!input.value) return;

    const [integer, fraction] = input.value.split('.');
    if (fraction !== undefined && fraction.length > 2) {
      input.value = `${integer}.${fraction.slice(0, 2)}`;
      item.controls.unitPrice.setValue(Number(input.value));
    }

    const value = Number(input.value);
    if (Number.isFinite(value) && value > 99_999_999_999_999_980) {
      input.value = '99999999999999980';
      item.controls.unitPrice.setValue(99_999_999_999_999_980);
    }
  }

  protected submit(): void {
    this.error = '';
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    this.submitting = true;
    const value = this.form.getRawValue();
    this.ordersService.create({
      customerName: value.customerName.trim(),
      customerEmail: value.customerEmail.trim(),
      items: value.items.map((item) => ({
        description: item.description.trim(),
        quantity: Number(item.quantity),
        unitPrice: Number(item.unitPrice),
      })),
    }).subscribe({
      next: (order) => this.router.navigate(['/orders', order.id], { state: { created: true } }),
      error: (error: unknown) => {
        this.error = this.errorMessage(error);
        this.submitting = false;
        this.changeDetector.markForCheck();
      },
    });
  }

  private createItem(): OrderItemForm {
    return this.formBuilder.group({
      description: ['', [
        Validators.required,
        Validators.maxLength(500),
        Validators.pattern(/^(?=.*\S)[^\p{C}<>]*$/u),
      ]],
      quantity: [1, [Validators.required, Validators.min(1), Validators.max(1_000_000)]],
      unitPrice: [0.01, [
        Validators.required,
        Validators.min(0.01),
        Validators.max(99_999_999_999_999_980),
        Validators.pattern(/^\d{1,17}(?:\.\d{1,2})?$/),
      ]],
    });
  }

  private errorMessage(error: unknown): string {
    if (error instanceof HttpErrorResponse && error.status === 0) {
      return 'Não foi possível conectar à API. Verifique se o backend está em execução.';
    }
    if (error instanceof HttpErrorResponse) {
      return error.error?.detail ?? error.error?.title ?? 'Não foi possível criar o pedido.';
    }
    return 'Ocorreu um erro inesperado ao criar o pedido.';
  }
}
