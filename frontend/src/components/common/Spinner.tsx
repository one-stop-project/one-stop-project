export function Spinner({ size = 'md' }: { size?: 'sm' | 'md' | 'lg' }) {
  const sizeClass = {
    sm: 'w-4 h-4 border-2',
    md: 'w-8 h-8 border-[3px]',
    lg: 'w-12 h-12 border-4',
  }[size];

  return (
    <div className="flex items-center justify-center p-4">
      <div
        className={`${sizeClass} border-gray-200 border-t-primary-600 rounded-full animate-spin`}
      />
    </div>
  );
}

export function PageSpinner() {
  return (
    <div className="min-h-[50vh] flex items-center justify-center">
      <Spinner size="lg" />
    </div>
  );
}
