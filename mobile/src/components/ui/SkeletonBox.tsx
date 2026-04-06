import React, { useEffect, useRef } from 'react';
import { Animated, ViewStyle } from 'react-native';
import { useTheme } from '../../theme';

interface Props {
  width: number | string;
  height: number;
  borderRadius?: number;
}

export const SkeletonBox: React.FC<Props> = ({ width, height, borderRadius = 8 }) => {
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
    outputRange: [colors.skeletonBase, colors.skeletonHighlight],
  }) as unknown as string;

  const style: ViewStyle = {
    width,
    height,
    borderRadius,
    backgroundColor,
  };

  return <Animated.View style={style} />;
};

export default SkeletonBox;
