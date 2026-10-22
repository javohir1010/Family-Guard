// Эмблема FamilyGuard: ребёнок (точка в центре) внутри щита, родитель —
// точка на контуре. В режиме animated точка бежит по контуру и «рисует» щит,
// на следующем круге стирает его — используется как индикатор загрузки.
const SHIELD = "M32 8 L51 15 V31 C51 44 43 52 32 57 C21 52 13 44 13 31 V15 Z";

interface LogoProps {
  size?: number;
  animated?: boolean;
  /** Цвет знака; по умолчанию акцент темы. */
  color?: string;
  /** Цвет фона под знаком — им рисуется зазор вокруг точки-родителя. */
  cut?: string;
  className?: string;
}

export default function Logo({
  size = 32,
  animated = false,
  color = "rgb(var(--accent))",
  cut = "var(--surface)",
  className,
}: LogoProps) {
  return (
    <svg
      width={size}
      height={size}
      viewBox="0 0 64 64"
      className={className}
      role={animated ? "progressbar" : "img"}
      aria-label={animated ? "Загрузка" : "FamilyGuard"}
    >
      <path
        d={SHIELD}
        pathLength={100}
        fill="none"
        stroke={color}
        strokeWidth={5}
        strokeLinejoin="round"
        strokeDasharray={animated ? 100 : undefined}
      >
        {animated && (
          <animate
            attributeName="stroke-dashoffset"
            values="100;0;-100"
            dur="3.6s"
            repeatCount="indefinite"
          />
        )}
      </path>
      <circle cx={32} cy={32} r={9} fill={color} />
      {animated ? (
        <circle r={6} fill={color} stroke={cut} strokeWidth={3.5}>
          <animateMotion dur="1.8s" repeatCount="indefinite" path={SHIELD} />
        </circle>
      ) : (
        <circle cx={51} cy={15} r={6} fill={color} stroke={cut} strokeWidth={3.5} />
      )}
    </svg>
  );
}
