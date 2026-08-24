import type { ReactNode } from "react";

type Props = {
  icon: string;
  title: string;
  message: string;
  action?: ReactNode;
};

/** 空状態の共通表示（mockup/states.html .state-block）。次に取るべき行動を提示する */
export function StateBlock({ icon, title, message, action }: Props) {
  return (
    <div className="state-block">
      <div className="state-icon" aria-hidden="true">
        {icon}
      </div>
      <h3>{title}</h3>
      <p>{message}</p>
      {action}
    </div>
  );
}
