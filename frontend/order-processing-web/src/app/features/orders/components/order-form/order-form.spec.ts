import { registerLocaleData } from '@angular/common';
import localePt from '@angular/common/locales/pt';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of } from 'rxjs';
import { OrderFormComponent } from './order-form';
import { OrdersService } from '../../services/orders.service';

describe('OrderFormComponent API limits', () => {
  registerLocaleData(localePt, 'pt-BR');

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [OrderFormComponent],
      providers: [
        provideRouter([]),
        { provide: OrdersService, useValue: { create: () => of({ id: 'order-id' }) } },
      ],
    }).compileComponents();
  });

  it('sets text limits to the API field lengths', () => {
    const fixture = TestBed.createComponent(OrderFormComponent);
    fixture.detectChanges();
    const inputs = fixture.nativeElement.querySelectorAll('input');

    expect(inputs[0].maxLength).toBe(200);
    expect(inputs[1].maxLength).toBe(320);
    expect(inputs[2].maxLength).toBe(500);
    expect(inputs[3].max).toBe('1000000');
    expect(inputs[3].step).toBe('1');
    expect(inputs[4].max).toBe('99999999999999980');
    expect(inputs[4].step).toBe('0.01');
    fixture.destroy();
  });

  it('caps quantities and unit prices to API limits as they are entered', () => {
    const fixture = TestBed.createComponent(OrderFormComponent);
    fixture.detectChanges();
    const inputs = fixture.nativeElement.querySelectorAll('input');
    const quantity = inputs[3] as HTMLInputElement;
    const unitPrice = inputs[4] as HTMLInputElement;

    quantity.value = '1000001';
    quantity.dispatchEvent(new Event('input'));
    unitPrice.value = '999999999999999999';
    unitPrice.dispatchEvent(new Event('input'));

    expect(quantity.value).toBe('1000000');
    expect(unitPrice.value).toBe('99999999999999980');
    fixture.destroy();
  });

  it('truncates unit price precision to two decimal places', () => {
    const fixture = TestBed.createComponent(OrderFormComponent);
    fixture.detectChanges();
    const unitPrice = fixture.nativeElement.querySelectorAll('input')[4] as HTMLInputElement;

    unitPrice.value = '12.345';
    unitPrice.dispatchEvent(new Event('input'));

    expect(unitPrice.value).toBe('12.34');
    fixture.destroy();
  });
});
