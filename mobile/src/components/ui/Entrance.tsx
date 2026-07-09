import React, { useEffect, useRef } from 'react';
import { AccessibilityInfo, Animated, Easing, StyleProp, ViewStyle } from 'react-native';

interface EntranceProps {
  /** Atraso em ms para efeito de cascata entre blocos (protótipo: passos de 50ms) */
  delay?: number;
  /** rise = fade + sobe 12px (gf-screen); pop = fade + escala .9→1 (gf-pop) */
  kind?: 'rise' | 'pop';
  style?: StyleProp<ViewStyle>;
  children: React.ReactNode;
}

// Animação de entrada dos blocos de tela (gf-screen / gf-pop do protótipo).
// Com Reduce Motion ativo, renderiza direto no estado final.
export default function Entrance({ delay = 0, kind = 'rise', style, children }: EntranceProps) {
  const progress = useRef(new Animated.Value(0)).current;

  useEffect(() => {
    let mounted = true;
    AccessibilityInfo.isReduceMotionEnabled().then(reduce => {
      if (!mounted) return;
      if (reduce) {
        progress.setValue(1);
        return;
      }
      Animated.timing(progress, {
        toValue: 1,
        duration: 340,
        delay,
        easing: Easing.out(Easing.cubic),
        useNativeDriver: true,
      }).start();
    });
    return () => { mounted = false; };
  }, [progress, delay]);

  const transform = kind === 'pop'
    ? [{ scale: progress.interpolate({ inputRange: [0, 1], outputRange: [0.9, 1] }) }]
    : [
        { translateY: progress.interpolate({ inputRange: [0, 1], outputRange: [12, 0] }) },
      ];

  return (
    <Animated.View style={[style, { opacity: progress, transform }]}>
      {children}
    </Animated.View>
  );
}
