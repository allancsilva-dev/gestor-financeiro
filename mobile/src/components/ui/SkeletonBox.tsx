import React, { useEffect, useRef } from 'react';
import { Animated, ViewStyle } from 'react-native';
import { useTheme } from '../../theme';

interface Props {
  width: number | `${number}%` | 'auto';
  height: number;
  borderRadius?: number;
  tone?: 'default' | 'inverse';
}

export const SkeletonBox: React.FC<Props> = ({ width, height, borderRadius = 8, tone = 'default' }) => {
  const colors = useTheme();
  const anim = useRef(new Animated.Value(0)).current;

  useEffect(() => {
    const loop = Animated.loop(
      Animated.timing(anim, {
        toValue: 1,
        duration: 900,
        useNativeDriver: false, // OBRIGATÓRIO: animação de backgroundColor
      })
    );
    loop.start();
    return () => loop.stop();
  }, [anim]);

  const backgroundColor = anim.interpolate({
    inputRange: [0, 1],
    outputRange: tone === 'inverse'
      ? ['rgba(255,255,255,0.14)', 'rgba(255,255,255,0.28)']
      : [colors.skeletonBase, colors.skeletonHighlight],
  }) as unknown as string;

  const style: ViewStyle = {
    width: width as any,
    height,
    borderRadius,
    backgroundColor,
  };

  return <Animated.View style={style} />;
};

export default SkeletonBox;
