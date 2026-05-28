import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import toast from 'react-hot-toast';
import {
  cartApi,
  CartResponse,
  AddCartItemRequest,
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
 * ★ 기준값은 itemId (백엔드 옵션 ID)
 */
export function useUpdateCartItemMutation() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: ({ itemId, quantity }: { itemId: number; quantity: number }) =>
      cartApi.updateQuantity(itemId, { quantity }),

    onMutate: async ({ itemId, quantity }) => {
      await queryClient.cancelQueries({ queryKey: CART_KEY });
      const previous = queryClient.getQueryData<CartResponse>(CART_KEY);

      if (previous) {
        // content 배열에서 itemId로 찾아 수량 변경
        const newContent = previous.content.map((item) =>
          item.itemId === itemId ? { ...item, quantity } : item
        );

        // totalPrice 재계산
        const newTotalPrice = newContent.reduce(
          (sum: number, i) => sum + i.price * i.quantity,
          0
        );

        queryClient.setQueryData<CartResponse>(CART_KEY, {
          ...previous,
          content: newContent,
          totalPrice: newTotalPrice,
        });
      }

      return { previous };
    },

    onError: (_err, _vars, context) => {
      if (context?.previous) {
        queryClient.setQueryData(CART_KEY, context.previous);
      }
    },

    onSettled: () => {
      queryClient.invalidateQueries({ queryKey: CART_KEY });
    },
  });
}

export function useRemoveCartItemMutation() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (itemId: number) => cartApi.removeItem(itemId),

    onMutate: async (itemId) => {
      await queryClient.cancelQueries({ queryKey: CART_KEY });
      const previous = queryClient.getQueryData<CartResponse>(CART_KEY);

      if (previous) {
        const newContent = previous.content.filter((i) => i.itemId !== itemId);
        queryClient.setQueryData<CartResponse>(CART_KEY, {
          ...previous,
          content: newContent,
          totalPrice: newContent.reduce(
            (sum: number, i) => sum + i.price * i.quantity,
            0
          ),
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
