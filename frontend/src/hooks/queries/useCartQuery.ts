import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import toast from 'react-hot-toast';
import {
  cartApi,
  CartResponse,
  AddCartItemRequest,
  UpdateCartItemRequest,
} from '@/domains/cart/cartApi';
import { useAuthStore } from '@/store/useAuthStore';

const CART_KEY = ['cart'];

export function useCartQuery() {
  const isAuthenticated = useAuthStore((s) => s.isAuthenticated);

  return useQuery({
    queryKey: CART_KEY,
    queryFn: () => cartApi.getCart(),
    enabled: isAuthenticated,
    staleTime: 30 * 1000,
  });
}

export function useAddToCartMutation() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (data: AddCartItemRequest) => cartApi.addItem(data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: CART_KEY });
      toast.success('장바구니에 추가되었습니다.');
    },
  });
}

/**
 * 수량 변경 — 낙관적 업데이트
 *
 * 즉시 UI 반영 후 서버 응답 대기
 * 실패 시 롤백
 */
export function useUpdateCartItemMutation() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: ({ itemId, quantity }: { itemId: number; quantity: number }) =>
      cartApi.updateQuantity(itemId, { quantity }),

    // 1. 낙관적 업데이트
    onMutate: async ({ itemId, quantity }) => {
      await queryClient.cancelQueries({ queryKey: CART_KEY });
      const previous = queryClient.getQueryData<CartResponse>(CART_KEY);

      if (previous) {
        const newItems = previous.items.map((item) =>
          item.cartItemId === itemId
            ? {
                ...item,
                quantity,
                subtotal: item.price * quantity,
              }
            : item
        );

        const newTotalPrice = newItems.reduce((sum, i) => sum + i.subtotal, 0);
        const newTotalItems = newItems.length;

        queryClient.setQueryData<CartResponse>(CART_KEY, {
          ...previous,
          items: newItems,
          totalPrice: newTotalPrice,
          totalItems: newTotalItems,
        });
      }

      return { previous };
    },

    // 2. 실패 시 롤백
    onError: (_err, _vars, context) => {
      if (context?.previous) {
        queryClient.setQueryData(CART_KEY, context.previous);
      }
    },

    // 3. 성공/실패 후 서버 데이터로 동기화
    onSettled: () => {
      queryClient.invalidateQueries({ queryKey: CART_KEY });
    },
  });
}

export function useRemoveCartItemMutation() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (itemId: number) => cartApi.removeItem(itemId),

    // 낙관적 삭제
    onMutate: async (itemId) => {
      await queryClient.cancelQueries({ queryKey: CART_KEY });
      const previous = queryClient.getQueryData<CartResponse>(CART_KEY);

      if (previous) {
        const newItems = previous.items.filter((i) => i.cartItemId !== itemId);
        queryClient.setQueryData<CartResponse>(CART_KEY, {
          ...previous,
          items: newItems,
          totalItems: newItems.length,
          totalPrice: newItems.reduce((sum, i) => sum + i.subtotal, 0),
        });
      }

      return { previous };
    },

    onError: (_err, _vars, context) => {
      if (context?.previous) {
        queryClient.setQueryData(CART_KEY, context.previous);
      }
    },

    onSuccess: () => {
      toast.success('삭제되었습니다.');
    },

    onSettled: () => {
      queryClient.invalidateQueries({ queryKey: CART_KEY });
    },
  });
}
