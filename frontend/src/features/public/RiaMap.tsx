// A drawn map of the fictional Vila Ria: the lagoon, the salt pans, the hubs and a suggested route.
// Decorative only; the facts it hints at are in the text next to it.
export default function RiaMap() {
  return (
    <svg className="ria-map" viewBox="0 0 520 520" role="presentation">
      <defs>
        <pattern id="salt" width="14" height="14" patternUnits="userSpaceOnUse">
          <path d="M0 14 14 0" stroke="#e4dccb" strokeWidth="1" />
        </pattern>
      </defs>
      <rect width="520" height="520" rx="36" fill="#efe8da" />
      <path
        d="M-10 210C70 170 120 250 200 215s150-80 230-30 110 20 110 20v170c-60-20-120 40-200 10s-140-60-220-20-110 10-130 0Z"
        fill="#bfd6e2"
      />
      <path d="M40 300c50-24 100 18 150-6" fill="none" stroke="#a3c3d4" strokeWidth="2" />
      <path d="M260 330c40-20 90 14 140-8" fill="none" stroke="#a3c3d4" strokeWidth="2" />
      <path d="M120 250c30-14 60 8 90-4" fill="none" stroke="#a3c3d4" strokeWidth="2" />
      <g fill="url(#salt)" stroke="#d9cfba">
        <rect x="300" y="400" width="80" height="46" rx="4" />
        <rect x="392" y="400" width="80" height="46" rx="4" />
        <rect x="300" y="456" width="80" height="40" rx="4" />
        <rect x="392" y="456" width="80" height="40" rx="4" />
      </g>
      <g stroke="#d9cfba" fill="#e6ddcb">
        <rect x="70" y="80" width="54" height="40" rx="5" />
        <rect x="134" y="70" width="40" height="50" rx="5" />
        <rect x="70" y="128" width="104" height="30" rx="5" />
      </g>
      <path
        className="ria-route"
        d="M150 165c40 30 90 18 120 40s20 90 40 140 70 60 110 70"
        fill="none"
        stroke="#c4421c"
        strokeWidth="3.5"
        strokeLinecap="round"
        strokeDasharray="2 9"
      />
      <g>
        <circle cx="150" cy="165" r="11" fill="#10253a" />
        <circle cx="150" cy="165" r="4" fill="#f6f2ea" />
        <circle cx="312" cy="345" r="9" fill="#10253a" />
        <circle cx="420" cy="415" r="14" fill="#c4421c" stroke="#fffdf8" strokeWidth="4" />
        <circle cx="440" cy="120" r="9" fill="#10253a" />
      </g>
    </svg>
  );
}
