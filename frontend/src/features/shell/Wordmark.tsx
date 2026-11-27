// The RiaRide wordmark: "ria" in the ink, "ride" in buoy orange.
export default function Wordmark({
  size = 'md',
  tone = 'ink',
}: {
  size?: 'md' | 'lg';
  tone?: 'ink' | 'on-ink';
}) {
  return (
    <span className={`wordmark wordmark-${size} wordmark-${tone}`} translate="no">
      ria<span className="wordmark-accent">ride</span>
    </span>
  );
}
